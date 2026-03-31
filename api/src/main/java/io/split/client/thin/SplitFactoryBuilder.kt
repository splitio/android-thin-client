package io.split.client.thin

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ProcessLifecycleOwner
import io.split.android.client.network.HttpClientImpl
import io.split.android.client.service.executor.SplitTaskType
import io.split.android.client.submitter.RecorderSyncHelperImpl
import io.split.client.thin.events.CoroutineSplitTaskExecutor
import io.split.client.thin.events.DefaultEventSubmissionCoordinator
import io.split.client.thin.events.EventsPeriodicScheduler
import io.split.client.thin.events.EventsPushHandler
import io.split.client.thin.events.EventsRecorderTask
import io.split.client.thin.events.EventsStorage
import io.split.client.thin.events.HttpEventsSubmitter
import io.split.client.thin.events.InBytesSizableStorageAdapter
import io.split.client.thin.http.createRetryableHttpClient
import io.split.client.thin.internal.AsyncBridge
import io.split.client.thin.internal.DefaultClientFactory
import io.split.client.thin.internal.DefaultClientManager
import io.split.client.thin.internal.DefaultSplitFactory
import io.split.client.thin.internal.auth.createAuthProvider
import io.split.client.thin.internal.evaluation.DefaultPollingScheduler
import io.split.client.thin.internal.evaluation.FetchReason
import io.split.client.thin.internal.evaluation.PollingScheduler
import io.split.client.thin.internal.evaluation.createEvaluationComponents
import io.split.client.thin.internal.evaluation.toEvaluationKey
import io.split.client.thin.internal.evaluation.toEvaluationTarget
import io.split.client.thin.internal.lifecycle.DefaultLifecycleManager
import io.split.client.thin.internal.lifecycle.LifecycleComponent
import io.split.client.thin.internal.observer.AndroidLoggerAdapter
import io.split.client.thin.internal.observer.DefaultCompositeObserver
import io.split.client.thin.internal.observer.LoggerObserver
import io.split.client.thin.internal.secure.EvaluationTarget
import io.split.client.thin.internal.secure.createSecureHttpClient
import io.split.client.thin.internal.streaming.StreamingComponents
import io.split.client.thin.internal.streaming.StreamingToken
import io.split.client.thin.internal.streaming.createStreamingComponents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// TODO: move these constants
private const val EVENTS_MAX_QUEUE_SIZE = 5000
private const val EVENTS_BATCH_SIZE = 500
private const val EVENTS_MAX_QUEUE_SIZE_IN_BYTES = 5_242_880L

/**
 * Builder for creating a [SplitFactory] instance.
 */
object SplitFactoryBuilder {

    private const val DEFAULT_AUTH_URL = "https://auth.split.io/api"
    private const val DEFAULT_EVALUATIONS_URL = "https://sdk.split.io/api/v2/evaluations"
    private const val DEFAULT_EVENTS_URL = "https://events.split.io/api/v1/events/bulk"
    private const val DEFAULT_TELEMETRY_URL = "https://telemetry.split.io/api/v1/metrics/config"
    private const val DEFAULT_STREAMING_URL = "https://streaming.split.io/sse"

    /**
     * Creates a factory configured with the SDK key, default target and config.
     */
    @JvmStatic
    @JvmOverloads
    fun build(
        sdkKey: SdkKey,
        defaultTarget: Target,
        config: SplitClientConfig? = null,
    ): SplitFactory {
        val httpClient = HttpClientImpl.Builder().build()
        val compositeObserver = DefaultCompositeObserver()
        compositeObserver.register(LoggerObserver(AndroidLoggerAdapter()))
        val retryableHttpClient = createRetryableHttpClient(httpClient, compositeObserver)
        val endpoints = config?.sync?.serviceEndpoints

        val defaultEvaluationTarget = defaultTarget.toEvaluationKey().toEvaluationTarget()

        val authProvider = createAuthProvider(
            retryableHttpClient = retryableHttpClient,
            sdkKey = sdkKey.sdkKey,
            authUrl = endpoints?.authUrl ?: DEFAULT_AUTH_URL,
            compositeObserver = compositeObserver,
            compositeKeyBuilder = { targets -> targets.sorted().joinToString(",") },
            defaultTarget = defaultEvaluationTarget.matchingKey,
        )

        val syncMode = config?.sync?.mode ?: SplitClientConfig.SyncMode.STREAMING

        var streamingComponents: StreamingComponents? = null

        val secureHttpClient = createSecureHttpClient(
            authProvider = authProvider,
            retryableHttpClient = retryableHttpClient,
            evaluationsUrl = endpoints?.evaluationsUrl ?: DEFAULT_EVALUATIONS_URL,
            eventsUrl = endpoints?.eventsUrl ?: DEFAULT_EVENTS_URL,
            telemetryUrl = endpoints?.telemetryUrl ?: DEFAULT_TELEMETRY_URL,
            sdkKey = sdkKey.sdkKey,
        )

        val (fetchCoordinator, evaluationRepository) = createEvaluationComponents(
            secureHttpClient = secureHttpClient,
            compositeObserver = compositeObserver,
        )
        val schedulerIntervalMillis = (config?.sync?.evaluationRefreshRate ?: 3600) * 1_000L

        // Single factory-level scope for all async operations
        val factoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // Event tracking components
        val eventsStorage = EventsStorage()
        val httpEventsSubmitter = HttpEventsSubmitter(secureHttpClient::postEvents)
        val eventsRecorderTask = EventsRecorderTask(
            storage = eventsStorage,
            submitter = httpEventsSubmitter,
            batchSize = EVENTS_BATCH_SIZE
        )
        val taskExecutor = CoroutineSplitTaskExecutor(factoryScope)
        val storageAdapter = InBytesSizableStorageAdapter(eventsStorage)
        val syncHelper = RecorderSyncHelperImpl(
            /* taskType = */ SplitTaskType.GENERIC_TASK,
            /* storage = */ storageAdapter,
            /* maxQueueSize = */ EVENTS_MAX_QUEUE_SIZE,
            /* maxQueueSizeInBytes = */ EVENTS_MAX_QUEUE_SIZE_IN_BYTES,
            /* splitTaskExecutor = */ taskExecutor
        )
        val eventsCoordinator = DefaultEventSubmissionCoordinator(
            scope = factoryScope,
            task = { eventsRecorderTask.execute() }
        )
        val pushRateMillis = (config?.sync?.pushRate ?: 1800) * 1_000L
        val eventsScheduler = EventsPeriodicScheduler(
            scope = factoryScope,
            coordinator = eventsCoordinator,
            pushRateMillis = pushRateMillis
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

        val clientManager = DefaultClientManager(
            scope = factoryScope,
            clientFactory = DefaultClientFactory(
                compositeObserver = compositeObserver,
                scope = factoryScope,
                evaluationRepository = evaluationRepository,
                filters = null,
                fallbackCalculator = DefaultSplitFactory.buildFallbackCalculator(config),
                onEventPush = eventsPushHandler,
                flushFn = { eventsCoordinator.flush() },
            ),
            authProvider = authProvider,
            onTargetsEmpty = { streamingComponents?.manager?.stopAll() },
        )

        // Factory function for creating polling scheduler
        fun createPollingScheduler(): PollingScheduler {
            return DefaultPollingScheduler(
                fetchCoordinator = fetchCoordinator,
                intervalMillis = schedulerIntervalMillis,
                scope = factoryScope,
                onPollTrigger = { interval ->
                    compositeObserver.notifyEvent(
                        io.split.client.thin.internal.observer.ObservableEvent(
                            type = io.split.client.thin.internal.observer.ObservableEventType.POLL_TRIGGER,
                            properties = mapOf("rate" to "${interval / 1000}s")
                        )
                    )
                }
            )
        }

        // Polling scheduler - created on-demand
        var pollingScheduler: PollingScheduler? = null
        val schedulerLock = Any()

        if (syncMode == SplitClientConfig.SyncMode.STREAMING) {
            streamingComponents = createStreamingComponents(
                streamingUrl = endpoints?.streamingUrl ?: DEFAULT_STREAMING_URL,
                retryableHttpClient = retryableHttpClient,
                tokenProvider = {
                    val cred = authProvider.credential()
                    StreamingToken(cred.token, cred.connDelaySeconds, cred.pushEnabled)
                },
                onEvaluationFetchNotification = { fetchCoordinator.refetchAll(null, FetchReason.PUSH) },
                onPushDisabled = {
                    fetchCoordinator.refetchAll(null, FetchReason.PERIODIC)
                    synchronized(schedulerLock) {
                        if (pollingScheduler == null) {
                            pollingScheduler = createPollingScheduler()
                            val scheduler = pollingScheduler!!
                            // Register with lifecycle manager
                            lifecycleManager.register(object : LifecycleComponent {
                                override fun pause() = scheduler.pause()
                                override fun resume() = scheduler.resume()
                            })
                        }
                        pollingScheduler!!.start()
                    }
                },
            )
        } else {
            // Polling mode - create and start immediately
            pollingScheduler = createPollingScheduler()
            pollingScheduler!!.start()
        }

        streamingComponents?.let { components ->
            lifecycleManager.register(object : LifecycleComponent {
                override fun pause() = components.manager.pause()
                override fun resume() = components.manager.resume()
            })
        }

        // Register polling scheduler with lifecycle manager (if created for polling mode)
        if (syncMode == SplitClientConfig.SyncMode.POLLING) {
            pollingScheduler?.let { scheduler ->
                lifecycleManager.register(object : LifecycleComponent {
                    override fun pause() = scheduler.pause()
                    override fun resume() = scheduler.resume()
                })
            }
        }

        streamingComponents?.startTrigger()

        return DefaultSplitFactory(
            defaultTarget = defaultTarget,
            config = config,
            asyncBridge = AsyncBridge(),
            evaluationRepository = evaluationRepository,
            filters = null,
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

}
