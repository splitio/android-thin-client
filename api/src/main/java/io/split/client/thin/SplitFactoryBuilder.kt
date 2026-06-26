package io.split.client.thin

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ProcessLifecycleOwner
import io.split.android.client.network.HttpClient
import io.split.android.client.network.HttpClientImpl
import io.split.android.client.service.executor.SplitTaskType
import io.split.android.client.submitter.RecorderSyncHelperImpl
import io.split.android.client.submitter.StoragePusher
import io.split.android.client.tracker.TrackerEvent
import io.split.client.thin.events.CoroutineSplitTaskExecutor
import io.split.client.thin.events.DefaultEventSubmissionCoordinator
import io.split.client.thin.events.EventsPeriodicScheduler
import io.split.client.thin.events.EventsPushHandler
import io.split.client.thin.events.EventsRecorderTask
import io.split.client.thin.events.HttpEventsSubmitter
import io.split.client.thin.events.InBytesSizableStorageAdapter
import io.split.client.thin.internal.persistence.ObserverEvaluationPersistenceCallbacks
import io.split.client.thin.internal.persistence.ObserverEventsPersistenceCallbacks
import io.split.client.thin.internal.persistence.domain.PersistenceConfig
import io.split.client.thin.internal.persistence.domain.createPersistenceDomainComponents
import io.split.client.thin.internal.createRetryableHttpClient
import io.split.client.thin.internal.http.HttpClientAdapter
import io.split.client.thin.internal.AsyncBridge
import io.split.client.thin.internal.DefaultInputValidator
import io.split.client.thin.internal.DefaultClientFactory
import io.split.client.thin.internal.ClientManager
import io.split.client.thin.internal.DefaultClientManager
import io.split.client.thin.internal.DefaultSplitFactory
import io.split.client.thin.internal.NoOpSplitFactory
import io.split.client.thin.internal.RuntimeSyncModeController
import io.split.client.thin.internal.auth.createAuthProvider
import io.split.client.thin.internal.evaluation.DefaultPollingScheduler
import io.split.client.thin.internal.evaluation.DefaultSyncDelayCalculator
import io.split.client.thin.internal.evaluation.FetchReason
import io.split.client.thin.internal.evaluation.SyncDelayCalculator
import io.split.client.thin.internal.evaluation.PollingScheduler
import io.split.client.thin.internal.evaluation.createEvaluationComponents
import io.split.client.thin.internal.evaluation.toEvaluationKey
import io.split.client.thin.internal.evaluation.toEvaluationTarget
import io.split.client.thin.internal.lifecycle.DefaultLifecycleManager
import io.split.client.thin.internal.lifecycle.LifecycleComponent
import io.split.client.thin.internal.observer.AndroidLoggerAdapter
import io.split.client.thin.internal.observer.CompositeObserver
import io.split.client.thin.internal.observer.DefaultCompositeObserver
import io.split.client.thin.internal.observer.LoggerObserver
import io.split.client.thin.internal.observer.ObservableEvent
import io.split.client.thin.internal.observer.ObservableEventType
import io.split.client.thin.internal.secure.EvaluationFilters
import io.split.android.client.streaming.support.CompressionUtilProvider
import io.split.client.thin.internal.EvaluationUpdateNotificationHandler
import io.split.client.thin.internal.evaluation.EvaluationKey
import io.split.client.thin.internal.secure.createSecureHttpClient
import io.split.client.thin.internal.streaming.EvaluationPayloadDecoder
import io.split.client.thin.internal.streaming.EvaluationUpdateNotification
import io.split.client.thin.internal.streaming.StreamingComponents
import io.split.client.thin.internal.streaming.StreamingToken
import io.split.client.thin.internal.streaming.createStreamingComponents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.atomic.AtomicLong

private const val EVENTS_MAX_QUEUE_SIZE = 5000
private const val EVENTS_BATCH_SIZE = 500
private const val EVENTS_MAX_QUEUE_SIZE_IN_BYTES = 5_242_880L

/**
 * Builder for creating a [SplitFactory] instance.
 */
object SplitFactoryBuilder {

    private const val DEFAULT_AUTH_HOST        = "https://auth.split.io"
    private const val DEFAULT_EVALUATIONS_HOST = "https://evaluator.split.io"
    private const val DEFAULT_EVENTS_HOST      = "https://events.split.io"
    private const val DEFAULT_TELEMETRY_HOST   = "https://telemetry.split.io"
    private const val DEFAULT_STREAMING_HOST   = "https://streaming.split.io"

    private const val AUTH_PATH_BASE              = "/api/v3/auth?capabilities="
    private const val CAPABILITY_EVALUATOR        = "evaluator"
    private const val CAPABILITY_EVALUATOR_CONFIGS = "evaluatorWithConfigs"
    private const val EVALUATIONS_PATH = "/api/evaluations"
    private const val EVENTS_PATH      = "/api/events/bulk"
    private const val TELEMETRY_PATH   = "/api/v1/metrics/config"
    private const val STREAMING_PATH   = "/sse"

    private fun buildUrl(host: String, path: String): String = host.trimEnd('/') + path

    /**
     * Creates a factory configured with the SDK key, default target and config.
     */
    @JvmStatic
    @JvmOverloads
    fun build(
        context: Context,
        sdkKey: SdkKey,
        defaultTarget: Target,
        config: SplitClientConfig? = null,
    ): SplitFactory = buildInternal(context, sdkKey, defaultTarget, config)

    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun buildInternal(
        context: Context,
        sdkKey: SdkKey,
        defaultTarget: Target,
        config: SplitClientConfig? = null,
        configChangeDetectorFactory: ((Boolean) -> Boolean)? = null,
        persistenceConfigCapture: ((PersistenceConfig) -> Unit)? = null,
    ): SplitFactory {
        val inputValidator = DefaultInputValidator()
        if (!inputValidator.validateSdkKey(sdkKey) || !inputValidator.validateKey(defaultTarget.key)) {
            return NoOpSplitFactory
        }
        val androidHttpClient = HttpClientImpl.Builder().build()
        val httpClient = HttpClientAdapter(androidHttpClient)
        val compositeObserver = DefaultCompositeObserver()
        compositeObserver.register(LoggerObserver(AndroidLoggerAdapter()))
        val retryableHttpClient = createRetryableHttpClient(httpClient, compositeObserver)
        val endpoints = config?.sync?.serviceEndpoints

        val defaultEvaluationTarget = defaultTarget.toEvaluationKey().toEvaluationTarget()
        val factoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val syncMode = config?.sync?.mode ?: SplitClientConfig.SyncMode.STREAMING
        val runtimeSyncModeController = RuntimeSyncModeController(
            scope = factoryScope,
            initialSyncMode = syncMode.name,
            onRuntimeSyncModeChanged = { type, properties ->
                compositeObserver.notifyEvent(ObservableEvent(type = type, properties = properties))
            },
        )

        val capability = if (config?.configsEnabled == true) {
            CAPABILITY_EVALUATOR_CONFIGS
        } else {
            CAPABILITY_EVALUATOR
        }
        val authPath = AUTH_PATH_BASE + capability
        val authProvider = createAuthProvider(
            retryableHttpClient = retryableHttpClient,
            sdkKey = sdkKey.sdkKey,
            authUrl = buildUrl(endpoints?.auth ?: DEFAULT_AUTH_HOST, authPath),
            compositeObserver = compositeObserver,
            defaultTarget = defaultEvaluationTarget.matchingKey,
            onUnauthorized = { runtimeSyncModeController.switchToSingleSync() },
        )

        var streamingComponents: StreamingComponents? = null

        val secureHttpClient = createSecureHttpClient(
            authProvider = authProvider,
            retryableHttpClient = retryableHttpClient,
            evaluationsUrl = buildUrl(endpoints?.evaluations ?: DEFAULT_EVALUATIONS_HOST, EVALUATIONS_PATH),
            eventsUrl = buildUrl(endpoints?.events ?: DEFAULT_EVENTS_HOST, EVENTS_PATH),
            telemetryUrl = buildUrl(DEFAULT_TELEMETRY_HOST, TELEMETRY_PATH),
            sdkKey = sdkKey.sdkKey,
        )

        val schedulerIntervalMillis = (config?.sync?.pollingRate ?: 3600) * 1_000L

        // Persistence components
        val persistenceConfig = PersistenceConfig(prefix = config?.storage?.prefix, sdkKey = sdkKey.sdkKey, dynamicConfig = config?.configsEnabled ?: false, flagSets = config?.filters?.flagSets)
        persistenceConfigCapture?.invoke(persistenceConfig)
        val persistenceComponents = createPersistenceDomainComponents(
            context = context.applicationContext,
            config = persistenceConfig,
            configChangeDetectorFactory = configChangeDetectorFactory,
            evaluationCallbacks = ObserverEvaluationPersistenceCallbacks(
                compositeObserver,
                cacheLoadedPayloadBuilder = { _, lastUpdateTimestamp -> buildCacheLoadedPayload(lastUpdateTimestamp) },
            ),
            eventsCallbacks = ObserverEventsPersistenceCallbacks(compositeObserver),
            scope = factoryScope
        )

        val (fetchCoordinator, evaluationRepository, evalReadStorage, evalWriteStorage) = createEvaluationComponents(
            secureHttpClient = secureHttpClient,
            compositeObserver = compositeObserver,
            cacheLoader = persistenceComponents.evaluationPersistenceManager,
            cacheLoadedPayloadBuilder = { _, lastUpdateTimestamp ->
                buildCacheLoadedPayload(lastUpdateTimestamp)
            },
            evaluationsUpdatedPayloadBuilder = { _, reason, changedFlagNames, isCacheLoaded ->
                buildEvaluationsUpdatedPayload(reason, changedFlagNames, isCacheLoaded)
            },
        )

        // Event tracking components
        val eventsStorage = persistenceComponents.eventsStorage
        val httpEventsSubmitter = HttpEventsSubmitter(secureHttpClient::postEvents)
        val eventsRecorderTask = EventsRecorderTask(
            storage = eventsStorage,
            submitter = httpEventsSubmitter,
            batchSize = EVENTS_BATCH_SIZE
        )
        val eventsJob = SupervisorJob(parent = factoryScope.coroutineContext[Job])
        val eventsScope = CoroutineScope(eventsJob + Dispatchers.IO.limitedParallelism(1))
        val taskExecutor = CoroutineSplitTaskExecutor(eventsScope)
        // Safe: both EventsStorage and PersistentEventsStorage implement StoragePusher<TrackerEvent>;
        // the declared type is RecorderStorage<TrackerEvent> but the runtime type always implements both.
        @Suppress("UNCHECKED_CAST")
        val storageAdapter = InBytesSizableStorageAdapter(eventsStorage as StoragePusher<TrackerEvent>)
        val syncHelper = RecorderSyncHelperImpl(
            /* taskType = */ SplitTaskType.GENERIC_TASK,
            /* storage = */ storageAdapter,
            /* maxQueueSize = */ EVENTS_MAX_QUEUE_SIZE,
            /* maxQueueSizeInBytes = */ EVENTS_MAX_QUEUE_SIZE_IN_BYTES,
            /* splitTaskExecutor = */ taskExecutor
        )
        val eventsCoordinator = DefaultEventSubmissionCoordinator(
            scope = eventsScope,
            task = { eventsRecorderTask.execute() }
        )
        val pushRateMillis = (config?.sync?.pushRate ?: 1800) * 1_000L
        val eventsScheduler = EventsPeriodicScheduler(
            scope = factoryScope,
            coordinator = eventsCoordinator,
            pushRateMillis = pushRateMillis,
            initialDelayMillis = 5_000L,
        )
        val eventsPushHandler = EventsPushHandler(syncHelper, eventsCoordinator)

        val lifecycleManager = DefaultLifecycleManager(
            compositeObserver = compositeObserver,
            observerRegistrar = { observer ->
                Handler(Looper.getMainLooper()).post {
                    ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
                }
            },
            observerUnregistrar = { observer ->
                Handler(Looper.getMainLooper()).post {
                    ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
                }
            },
        )

        val evaluationFilters = EvaluationFilters(
            sets = config?.filters?.flagSets?.takeIf { it.isNotEmpty() } ?: emptySet(),
            configs = config?.configsEnabled == true,
        )

        // Polling scheduler - created on-demand
        var pollingScheduler: PollingScheduler? = null
        val schedulerLock = Any()

        // Helper to create and register scheduler
        fun getOrCreateScheduler(): PollingScheduler = synchronized(schedulerLock) {
            pollingScheduler ?: DefaultPollingScheduler(
                fetchCoordinator = fetchCoordinator,
                intervalMillis = schedulerIntervalMillis,
                evaluationFilters = evaluationFilters,
                scope = factoryScope,
                onPollTrigger = { interval ->
                    compositeObserver.notifyEvent(
                        ObservableEvent(
                            type = ObservableEventType.POLL_TRIGGER,
                            properties = mapOf("rate" to "${interval / 1000}s")
                        )
                    )
                }
            ).also { scheduler ->
                pollingScheduler = scheduler
                runtimeSyncModeController.setPollingScheduler(scheduler)
                // Register with lifecycle manager
                lifecycleManager.register(object : LifecycleComponent {
                    override fun pause() = scheduler.pause()
                    override fun resume() = runtimeSyncModeController.resumePolling(scheduler)
                })
            }
        }

        // Shared across the streaming connection manager and the fetch handler so an in-flight
        // (parked) eval fetch catches up to the latest change number from newer notifications.
        val streamingTargetChangeNumber = AtomicLong(Long.MIN_VALUE)

        createAndRegisterStreaming(
            syncMode = syncMode,
            streamingUrl = buildUrl(endpoints?.streaming ?: DEFAULT_STREAMING_HOST, STREAMING_PATH),
            httpClient = androidHttpClient,
            parentScope = factoryScope,
            tokenProvider = {
                val cred = authProvider.credential()
                StreamingToken(cred.token, cred.connDelaySeconds, cred.pushEnabled)
            },
            invalidateToken = { authProvider.invalidateAll() },
            onEvaluationFetchNotification = EvaluationUpdateNotificationHandler(
                decoder = EvaluationPayloadDecoder(CompressionUtilProvider()),
                fetchCoordinator = fetchCoordinator,
                evaluationFilters = evaluationFilters,
                delayProvider = { notification -> buildDelayProvider(notification) },
                onPushHandlingError = { t ->
                    compositeObserver.notifyEvent(
                        ObservableEvent(
                            type = ObservableEventType.EVAL_FETCH_FAILED,
                            properties = mapOf("error" to (t.message ?: "unknown"))
                        )
                    )
                },
                freshnessChecker = { evalKey -> evalReadStorage.lastChangeNumber(evalKey) },
                cdnBypassBackoffBaseMs = BuildConfig.CDN_BYPASS_BACKOFF_BASE_MS,
                targetChangeNumberProvider = { streamingTargetChangeNumber.get() },
            )::handle,
            onPushDisabled = {
                fetchCoordinator.refetchAll(evaluationFilters, FetchReason.PERIODIC)
                runtimeSyncModeController.startPollingIfAllowed { getOrCreateScheduler() }
            },
            onPushEnabled = {
                // Push came back up: stop polling fallback and catch up over the live socket.
                // stop() is idempotent and single-sync never starts streaming, so onPushEnabled
                // cannot fire in single-sync — calling stop() directly is safe.
                synchronized(schedulerLock) { pollingScheduler }?.stop()
                fetchCoordinator.refetchAll(evaluationFilters, FetchReason.PERIODIC)
            },
            lifecycleManager = lifecycleManager,
            onPollingMode = { runtimeSyncModeController.startPollingIfAllowed { getOrCreateScheduler() } },
            runtimeSyncModeController = runtimeSyncModeController,
            observer = compositeObserver,
            evalChangeNumberHolder = streamingTargetChangeNumber,
        )?.also { components ->
            streamingComponents = components
        }

        var clientManagerRef: ClientManager? = null
        val clientManager = DefaultClientManager(
            scope = factoryScope,
            clientFactory = DefaultClientFactory(
                compositeObserver = compositeObserver,
                scope = factoryScope,
                evaluationRepository = evaluationRepository,
                filters = evaluationFilters,
                fallbackCalculator = DefaultSplitFactory.buildFallbackCalculator(config),
                onEventPush = eventsPushHandler,
                flushFn = { eventsCoordinator.flush() },
                onInitFetchComplete = streamingComponents?.let { components ->
                    { runtimeSyncModeController.startStreamingIfAllowed(components.startTrigger) }
                },
                inputValidator = inputValidator,
                authProvider = authProvider,
                fetchCoordinator = fetchCoordinator,
                evaluationStorage = evalWriteStorage,
                deregister = { key -> clientManagerRef?.destroy(key) },
            ),
            authProvider = authProvider,
            onTargetsEmpty = {
                streamingComponents?.manager?.stopAll()
                pollingScheduler?.stop()
                eventsScheduler.stop()
                lifecycleManager.destroy()
            },
        )
        clientManagerRef = clientManager

        return DefaultSplitFactory(
            defaultTarget = defaultTarget,
            config = config,
            asyncBridge = AsyncBridge(),
            evaluationRepository = evaluationRepository,
            filters = evaluationFilters,
            fetchCoordinator = fetchCoordinator,
            pollingScheduler = pollingScheduler,
            eventsScheduler = eventsScheduler,
            eventsCoordinator = eventsCoordinator,
            scope = factoryScope,
            compositeObserver = compositeObserver,
            lifecycleManager = lifecycleManager,
            clientManager = clientManager,
        )
    }

    private fun createAndRegisterStreaming(
        syncMode: SplitClientConfig.SyncMode,
        streamingUrl: String,
        httpClient: HttpClient,
        parentScope: CoroutineScope,
        tokenProvider: suspend () -> StreamingToken,
        invalidateToken: suspend () -> Unit,
        onEvaluationFetchNotification: suspend (EvaluationUpdateNotification?) -> Unit,
        onPushDisabled: suspend () -> Unit,
        onPushEnabled: suspend () -> Unit,
        lifecycleManager: DefaultLifecycleManager,
        onPollingMode: () -> Unit,
        runtimeSyncModeController: RuntimeSyncModeController,
        observer: CompositeObserver,
        evalChangeNumberHolder: AtomicLong,
    ): StreamingComponents? {
        return if (syncMode == SplitClientConfig.SyncMode.STREAMING) {
            createStreamingComponents(
                streamingUrl = streamingUrl,
                httpClient = httpClient,
                parentScope = parentScope,
                tokenProvider = tokenProvider,
                invalidateToken = invalidateToken,
                onEvaluationFetchNotification = onEvaluationFetchNotification,
                onPushDisabled = onPushDisabled,
                onPushEnabled = onPushEnabled,
                observer = observer,
                evalChangeNumberHolder = evalChangeNumberHolder,
            ).also { components ->
                runtimeSyncModeController.setStreamingManager(components.manager)
                lifecycleManager.register(object : LifecycleComponent {
                    override fun pause() = components.manager.pause()
                    override fun resume() = runtimeSyncModeController.resumeStreaming(components.manager)
                })
                // startStreamingIfAllowed is called via onInitFetchComplete after the init fetch completes
            }
        } else if (syncMode == SplitClientConfig.SyncMode.POLLING) {
            onPollingMode()
            null
        } else {
            null
        }
    }

}

internal fun buildCacheLoadedPayload(lastUpdateTimestamp: Long?): SdkReadyMetadata =
    SdkReadyMetadata(isInitialCacheLoad = false, lastUpdateTimestamp = lastUpdateTimestamp)

internal fun buildEvaluationsUpdatedPayload(
    reason: FetchReason,
    changedFlagNames: List<String>,
    isCacheLoaded: Boolean,
): Any? = when (reason) {
    FetchReason.INITIALIZATION -> SdkReadyMetadata(isInitialCacheLoad = !isCacheLoaded, lastUpdateTimestamp = null)
    else -> if (changedFlagNames.isEmpty()) null
    else SdkUpdateMetadata(type = SdkUpdateMetadata.Type.FLAGS_UPDATE, names = changedFlagNames)
}

internal fun buildDelayProvider(
    notification: EvaluationUpdateNotification?,
    calculator: SyncDelayCalculator = DefaultSyncDelayCalculator(),
): ((EvaluationKey) -> Long)? {
    return notification?.let { n ->
        { key -> calculator.calculateDelay(key.key.matchingKey, n.updateIntervalMs, n.algorithmSeed, n.hashingAlgorithm) }
    }
}
