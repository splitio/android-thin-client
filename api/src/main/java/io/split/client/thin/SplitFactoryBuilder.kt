package io.split.client.thin

import io.split.android.client.network.HttpClientImpl
import io.split.client.thin.http.createRetryableHttpClient
import io.split.client.thin.internal.AsyncBridge
import io.split.client.thin.internal.DefaultSplitFactory
import io.split.client.thin.internal.auth.createAuthProvider
import io.split.client.thin.internal.evaluation.DefaultEvaluationFetchCoordinator
import io.split.client.thin.internal.evaluation.DefaultEvaluationProvider
import io.split.client.thin.internal.evaluation.DefaultEvaluationRepository
import io.split.client.thin.internal.evaluation.FetchReason
import io.split.client.thin.internal.evaluation.InMemoryEvaluationStorage
import io.split.client.thin.internal.evaluation.JsonEvaluationResponseDeserializer
import io.split.client.thin.internal.evaluation.toEvaluationKey
import io.split.client.thin.internal.evaluation.toEvaluationTarget
import io.split.client.thin.internal.observer.DefaultCompositeObserver
import io.split.client.thin.internal.observer.ObservableEvent
import io.split.client.thin.internal.observer.ObservableEventType
import io.split.client.thin.internal.secure.EvaluationTarget
import io.split.client.thin.internal.secure.createSecureHttpClient

/**
 * Builder for creating a [SplitFactory] instance.
 */
object SplitFactoryBuilder {

    private const val DEFAULT_AUTH_URL = "https://auth.split.io/api"
    private const val DEFAULT_EVALUATIONS_URL = "https://sdk.split.io/api/v2/evaluations"
    private const val DEFAULT_EVENTS_URL = "https://events.split.io/api/v1/events/bulk"
    private const val DEFAULT_TELEMETRY_URL = "https://telemetry.split.io/api/v1/metrics/config"

    /**
     * Creates a factory configured with the SDK key, default target, config and HTTP client.
     *
     * URL overrides can be provided via [SplitClientConfig.SyncConfig.serviceEndpoints];
     * each field falls back to a built-in default when null.
     */
    @JvmStatic
    @JvmOverloads
    fun build(
        sdkKey: SdkKey,
        defaultTarget: Target,
        config: SplitClientConfig? = null,
    ): SplitFactory {
        val httpClient = HttpClientImpl.Builder().build()
        val retryableHttpClient = createRetryableHttpClient(httpClient)
        val endpoints = config?.sync?.serviceEndpoints

        val authProvider = createAuthProvider<EvaluationTarget>(
            retryableHttpClient = retryableHttpClient,
            sdkKey = sdkKey.sdkKey,
            authUrl = endpoints?.authUrl ?: DEFAULT_AUTH_URL,
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

        val storage = InMemoryEvaluationStorage()
        val compositeObserver = DefaultCompositeObserver()
        val evaluationResponseDeserializer = JsonEvaluationResponseDeserializer()
        val provider = DefaultEvaluationProvider(secureHttpClient, evaluationResponseDeserializer)
        val fetchCoordinator = DefaultEvaluationFetchCoordinator(
            provider = provider,
            readStorage = storage,
            writeStorage = storage,
            onFetchSuccess = { reason ->
                val eventType = when (reason) {
                    FetchReason.INITIALIZATION, FetchReason.TARGET_SWITCH ->
                        ObservableEventType.EVAL_STORAGE_UPDATED
                    FetchReason.PERIODIC, FetchReason.PUSH ->
                        ObservableEventType.EVALUATIONS_UPDATED
                }
                compositeObserver.notifyEvent(ObservableEvent(eventType))
            },
        )
        val evaluationRepository = DefaultEvaluationRepository(storage, fetchCoordinator)

        return DefaultSplitFactory(
            defaultTarget = defaultTarget,
            config = config,
            asyncBridge = AsyncBridge(),
            evaluationRepository = evaluationRepository,
            filters = null,
            readStorage = storage,
            compositeObserver = compositeObserver,
        )
    }
}
