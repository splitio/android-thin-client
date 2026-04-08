package io.split.client.thin.internal.evaluation

import io.split.client.thin.internal.observer.CompositeObserver
import io.split.client.thin.internal.observer.ObservableEvent
import io.split.client.thin.internal.observer.ObservableEventType
import io.split.client.thin.internal.secure.SecureHttpClient

data class EvaluationComponents(
    val fetchCoordinator: EvaluationFetchCoordinator,
    val repository: EvaluationRepository,
)

fun createEvaluationComponents(
    secureHttpClient: SecureHttpClient,
    compositeObserver: CompositeObserver,
    cacheLoader: EvaluationCacheLoader? = null,
): EvaluationComponents {
    val storage = InMemoryEvaluationStorage(
        cacheLoader = cacheLoader,
        onCacheLoaded = { evalKey ->
            compositeObserver.notifyEvent(
                ObservableEvent(
                    type = ObservableEventType.EVAL_LOADED_FROM_STORAGE,
                    properties = mapOf("matchingKey" to evalKey.key.matchingKey)
                )
            )
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
    )
    val fetchCoordinator = DefaultEvaluationFetchCoordinator(
        provider = provider,
        readStorage = storage,
        writeStorage = storage,
        onEvaluationsUpdated = { evalKey, reason ->
            val eventType = when (reason) {
                FetchReason.INITIALIZATION ->
                    ObservableEventType.EVAL_STORAGE_UPDATED
                FetchReason.TARGET_SWITCH, FetchReason.PERIODIC, FetchReason.PUSH ->
                    ObservableEventType.EVALUATIONS_UPDATED
            }
            compositeObserver.notifyEvent(
                ObservableEvent(eventType, mapOf("matchingKey" to evalKey.key.matchingKey))
            )
        },
        onEvalFetchRequested = { evalKey, reason ->
            compositeObserver.notifyEvent(
                ObservableEvent(
                    type = ObservableEventType.EVAL_FETCH_REQUESTED,
                    properties = mapOf(
                        "matchingKey" to evalKey.key.matchingKey,
                        "reason" to reason.name
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
