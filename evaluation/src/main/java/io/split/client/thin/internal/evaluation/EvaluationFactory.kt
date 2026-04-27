package io.split.client.thin.internal.evaluation

import io.split.client.thin.internal.observer.CompositeObserver
import io.split.client.thin.internal.observer.ObservableEvent
import io.split.client.thin.internal.observer.ObservableEventType
import io.split.client.thin.internal.secure.SecureHttpClient
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

data class EvaluationComponents(
    val fetchCoordinator: EvaluationFetchCoordinator,
    val repository: EvaluationRepository,
)

fun createEvaluationComponents(
    secureHttpClient: SecureHttpClient,
    compositeObserver: CompositeObserver,
    cacheLoader: EvaluationCacheLoader? = null,
    cacheLoadedPayloadBuilder: ((EvaluationKey, Long?) -> Any?)? = null,
    evaluationsUpdatedPayloadBuilder: ((evalKey: EvaluationKey, reason: FetchReason, changedFlagNames: List<String>, isCacheLoaded: Boolean) -> Any?)? = null,
): EvaluationComponents {
    val cacheLoadedKeys: MutableSet<EvaluationKey> = Collections.newSetFromMap(ConcurrentHashMap())
    val storage = InMemoryEvaluationStorage(
        cacheLoader = cacheLoader,
        onCacheLoaded = { evalKey, _ ->
            cacheLoadedKeys.add(evalKey)
        },
    )
    val provider = DefaultEvaluationProvider(
        secureHttpClient = secureHttpClient,
        deserializer = JsonEvaluationResponseDeserializer(),
        onEvalFetchStarted = { evalKey ->
            compositeObserver.notifyEvent(
                ObservableEvent(
                    type = ObservableEventType.EVAL_FETCH_STARTED,
                    properties = mapOf("matchingKey" to evalKey.key.matchingKey)
                )
            )
        },
        onEvalDeserializeFailed = { evalKey, error ->
            compositeObserver.notifyEvent(
                ObservableEvent(
                    type = ObservableEventType.EVAL_DESERIALIZE_FAILED,
                    properties = mapOf(
                        "matchingKey" to evalKey.key.matchingKey,
                        "error" to error.message.orEmpty()
                    )
                )
            )
        },
        onEmptyResponseBody = { evalKey ->
            compositeObserver.notifyEvent(
                ObservableEvent(
                    type = ObservableEventType.EVAL_EMPTY_RESPONSE_BODY,
                    properties = mapOf("matchingKey" to evalKey.key.matchingKey)
                )
            )
        },
    )
    val fetchCoordinator = DefaultEvaluationFetchCoordinator(
        provider = provider,
        readStorage = storage,
        writeStorage = storage,
        onEvaluationsUpdated = { evalKey, reason, changedFlagNames ->
            val payload = evaluationsUpdatedPayloadBuilder?.invoke(evalKey, reason, changedFlagNames, cacheLoadedKeys.contains(evalKey))
            when (reason) {
                FetchReason.INITIALIZATION -> compositeObserver.notifyEvent(
                    ObservableEvent(
                        type = ObservableEventType.EVAL_STORAGE_UPDATED,
                        properties = mapOf("matchingKey" to evalKey.key.matchingKey),
                        payload = payload,
                    )
                )
                FetchReason.TARGET_SWITCH, FetchReason.PERIODIC, FetchReason.PUSH -> {
                    if (payload != null || evaluationsUpdatedPayloadBuilder == null) {
                        compositeObserver.notifyEvent(
                            ObservableEvent(
                                type = ObservableEventType.EVALUATIONS_UPDATED,
                                properties = mapOf("matchingKey" to evalKey.key.matchingKey),
                                payload = payload,
                            )
                        )
                    }
                }
            }
        },
        onEvalFetchRequested = { evalKey, reason, delayMs ->
            compositeObserver.notifyEvent(
                ObservableEvent(
                    type = ObservableEventType.EVAL_FETCH_REQUESTED,
                    properties = mapOf(
                        "matchingKey" to evalKey.key.matchingKey,
                        "reason" to reason.name,
                        "delayMs" to if (delayMs > 0L) " (delayed: ${delayMs}ms)" else "",
                    )
                )
            )
        },
        onEvalFetchDeduped = { evalKey ->
            compositeObserver.notifyEvent(
                ObservableEvent(
                    type = ObservableEventType.EVAL_FETCH_DEDUPED,
                    properties = mapOf("matchingKey" to evalKey.key.matchingKey)
                )
            )
        },
        onEvalFetchSucceeded = { evalKey ->
            compositeObserver.notifyEvent(
                ObservableEvent(
                    type = ObservableEventType.EVAL_FETCH_SUCCEEDED,
                    properties = mapOf("matchingKey" to evalKey.key.matchingKey)
                )
            )
        },
        onEvalFetchFailed = { evalKey, error ->
            compositeObserver.notifyEvent(
                ObservableEvent(
                    type = ObservableEventType.EVAL_FETCH_FAILED,
                    properties = mapOf(
                        "matchingKey" to evalKey.key.matchingKey,
                        "error" to error.message.orEmpty()
                    )
                )
            )
        },
    )
    return EvaluationComponents(
        fetchCoordinator = fetchCoordinator,
        repository = DefaultEvaluationRepository(storage, fetchCoordinator, persistenceBackedStorage = storage),
    )
}
