package io.split.client.thin

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ProcessLifecycleOwner
import io.split.android.client.network.HttpClientImpl
import io.split.client.thin.http.createRetryableHttpClient
import io.split.client.thin.internal.AsyncBridge
import io.split.client.thin.internal.DefaultSplitFactory
import io.split.client.thin.internal.auth.createAuthProvider
import io.split.client.thin.internal.evaluation.FetchReason
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
import io.split.client.thin.internal.streaming.createStreamingComponents

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

        val authProvider = createAuthProvider<EvaluationTarget>(
            retryableHttpClient = retryableHttpClient,
            sdkKey = sdkKey.sdkKey,
            authUrl = endpoints?.authUrl ?: DEFAULT_AUTH_URL,
            compositeObserver = compositeObserver,
            compositeKeyBuilder = { targets ->
                EvaluationTarget(
                    matchingKey = targets.joinToString(",") { it.matchingKey },
                    bucketingKey = null,
                    attributes = null,
                )
            },
        )

        val defaultEvaluationTarget = defaultTarget.toEvaluationKey().toEvaluationTarget()

        val syncMode = config?.sync?.mode ?: SplitClientConfig.SyncMode.STREAMING

        // Track latest streaming targets for multi-user composite JWT in the tokenProvider.
        // Updated atomically before streaming is started so the token is always fresh.
        var latestStreamingTargets: Set<EvaluationTarget> = setOf(defaultEvaluationTarget)
        var streamingComponents: StreamingComponents? = null

        val secureHttpClient = createSecureHttpClient(
            authProvider = authProvider,
            retryableHttpClient = retryableHttpClient,
            defaultTarget = defaultEvaluationTarget,
            evaluationsUrl = endpoints?.evaluationsUrl ?: DEFAULT_EVALUATIONS_URL,
            eventsUrl = endpoints?.eventsUrl ?: DEFAULT_EVENTS_URL,
            telemetryUrl = endpoints?.telemetryUrl ?: DEFAULT_TELEMETRY_URL,
            sdkKey = sdkKey.sdkKey,
            onStreamingTargetsChanged = { targets ->
                latestStreamingTargets = targets
                streamingComponents?.startTrigger()
            },
            onStreamingEmpty = { streamingComponents?.manager?.stopAll() },
        )

        val (fetchCoordinator, evaluationRepository) = createEvaluationComponents(
            secureHttpClient = secureHttpClient,
            compositeObserver = compositeObserver,
        )
        if (syncMode == SplitClientConfig.SyncMode.STREAMING) {
            streamingComponents = createStreamingComponents(
                streamingUrl = endpoints?.streamingUrl ?: DEFAULT_STREAMING_URL,
                retryableHttpClient = retryableHttpClient,
                tokenProvider = { authProvider.credential(latestStreamingTargets).token },
                onEvaluationFetchNotification = { fetchCoordinator.refetchAll(null, FetchReason.PUSH) },
            )
        }
        val schedulerIntervalMillis = (config?.sync?.evaluationRefreshRate ?: 3600) * 1_000L

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
        streamingComponents?.let { components ->
            lifecycleManager.register(object : LifecycleComponent {
                override fun pause() = components.manager.pause()
                override fun resume() = components.manager.resume()
            })
        }

        return DefaultSplitFactory(
            defaultTarget = defaultTarget,
            config = config,
            asyncBridge = AsyncBridge(),
            evaluationRepository = evaluationRepository,
            filters = null,
            fetchCoordinator = fetchCoordinator,
            schedulerIntervalMillis = schedulerIntervalMillis,
            compositeObserver = compositeObserver,
            lifecycleManager = lifecycleManager,
            secureHttpClient = secureHttpClient,
        )
    }

}
