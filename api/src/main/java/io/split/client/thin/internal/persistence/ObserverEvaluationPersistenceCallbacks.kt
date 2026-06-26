package io.split.client.thin.internal.persistence

import io.split.client.thin.internal.evaluation.EvaluationKey
import io.split.client.thin.internal.evaluation.StoredEvaluation
import io.split.client.thin.internal.observer.CompositeObserver
import io.split.client.thin.internal.observer.ObservableEvent
import io.split.client.thin.internal.observer.ObservableEventType
import io.split.client.thin.internal.persistence.domain.evaluation.EvaluationPersistenceCallbacks

internal class ObserverEvaluationPersistenceCallbacks(
    private val compositeObserver: CompositeObserver,
    private val cacheLoadedPayloadBuilder: ((EvaluationKey, Long) -> Any?)? = null,
) : EvaluationPersistenceCallbacks {

    override fun onEvalStorageUpdated(evalKey: EvaluationKey, changeNumber: Long, evaluations: List<StoredEvaluation>) {
        compositeObserver.notifyEvent(ObservableEvent(ObservableEventType.EVAL_STORAGE_UPDATED, properties = mapOf("matchingKey" to evalKey.key.matchingKey)))
    }

    override fun onCacheLoaded(evalKey: EvaluationKey, lastUpdateTimestamp: Long, evaluations: List<StoredEvaluation>) {
        compositeObserver.notifyEvent(
            ObservableEvent(
                type = ObservableEventType.EVAL_STORAGE_LOAD_SUCCEEDED,
                properties = mapOf(
                    "matchingKey" to evalKey.key.matchingKey,
                    "lastUpdateTimestamp" to lastUpdateTimestamp.toString(),
                ),
                payload = cacheLoadedPayloadBuilder?.invoke(evalKey, lastUpdateTimestamp),
            )
        )
    }

    override fun onLoadStarted() {
        compositeObserver.notifyEvent(ObservableEvent(ObservableEventType.EVAL_STORAGE_LOAD_STARTED))
    }

    override fun onLoadFailed(error: String) {
        compositeObserver.notifyEvent(ObservableEvent(ObservableEventType.EVAL_STORAGE_LOAD_FAILED))
    }

    override fun onWriteScheduled() {
        compositeObserver.notifyEvent(ObservableEvent(ObservableEventType.EVAL_STORAGE_WRITE_SCHEDULED))
    }

    override fun onWriteSucceeded() {
        compositeObserver.notifyEvent(ObservableEvent(ObservableEventType.EVAL_STORAGE_WRITE_SUCCEEDED))
    }

    override fun onWriteFailed(error: String) {
        compositeObserver.notifyEvent(ObservableEvent(ObservableEventType.EVAL_STORAGE_WRITE_FAILED))
    }
}
