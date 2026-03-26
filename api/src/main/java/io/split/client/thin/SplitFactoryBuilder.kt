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
import io.split.client.thin.internal.evaluation.createEvaluationComponents
import io.split.client.thin.internal.evaluation.toEvaluationKey
import io.split.client.thin.internal.evaluation.toEvaluationTarget
import io.split.client.thin.internal.lifecycle.DefaultLifecycleManager
import io.split.client.thin.internal.observer.AndroidLoggerAdapter
import io.split.client.thin.internal.observer.DefaultCompositeObserver
import io.split.client.thin.internal.observer.LoggerObserver
import io.split.client.thin.internal.secure.EvaluationTarget
import io.split.client.thin.internal.secure.createSecureHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Builder for creating a [SplitFactory] instance.
 */
object SplitFactoryBuilder {

    private const val DEFAULT_AUTH_URL = "https://auth.split.io/api"
    private const val DEFAULT_EVALUATIONS_URL = "https://sdk.split.io/api/v2/evaluations"
    private const val DEFAULT_EVENTS_URL = "https://events.split.io/api/v1/events/bulk"
    private const val DEFAULT_TELEMETRY_URL = "https://telemetry.split.io/api/v1/metrics/config"

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

        val authProvider = createAuthProvider<EvaluationTarget>(
            retryableHttpClient = retryableHttpClient,
            sdkKey = sdkKey.sdkKey,
            authUrl = endpoints?.authUrl ?: DEFAULT_AUTH_URL,
            compositeObserver = compositeObserver,
        )

        val defaultEvaluationTarget = defaultTarget.toEvaluationKey().toEvaluationTarget()
        val secureHttpClient = createSecureHttpClient(
            authProvider = authProvider,
            retryableHttpClient = retryableHttpClient,
            defaultTarget = defaultEvaluationTarget,
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
            batchSize = 500
        )
        val taskExecutor = CoroutineSplitTaskExecutor(factoryScope)
        val storageAdapter = InBytesSizableStorageAdapter(eventsStorage)
        val syncHelper = RecorderSyncHelperImpl<io.split.android.client.submitter.InBytesSizable>(
            SplitTaskType.GENERIC_TASK,
            storageAdapter,
            5000,
            5_242_880L,
            taskExecutor
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

        return DefaultSplitFactory(
            defaultTarget = defaultTarget,
            config = config,
            asyncBridge = AsyncBridge(),
            evaluationRepository = evaluationRepository,
            filters = null,
            fetchCoordinator = fetchCoordinator,
            schedulerIntervalMillis = schedulerIntervalMillis,
            eventsScheduler = eventsScheduler,
            eventsCoordinator = eventsCoordinator,
            scope = factoryScope,
            compositeObserver = compositeObserver,
            lifecycleManager = lifecycleManager,
            clientManager = DefaultClientManager(
                factoryScope,
                DefaultClientFactory(
                    compositeObserver = compositeObserver,
                    scope = factoryScope,
                    evaluationRepository = evaluationRepository,
                    filters = null,
                    fallbackCalculator = DefaultSplitFactory.buildFallbackCalculator(config),
                    fetchCoordinator = fetchCoordinator,
                    schedulerIntervalMillis = schedulerIntervalMillis,
                    onEventPush = eventsPushHandler,
                    flushFn = { eventsCoordinator.flush() },
                    lifecycleManager = lifecycleManager,
                )
            ),
        )
    }
}
