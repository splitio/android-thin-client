package io.split.client.thin.internal.persistence

import io.split.client.thin.internal.evaluation.EvaluationKey
import io.split.client.thin.internal.evaluation.StoredEvaluation
import io.split.client.thin.internal.observer.CompositeObserver
import io.split.client.thin.internal.observer.ObservableEvent
import io.split.client.thin.internal.observer.ObservableEventType
import io.split.client.thin.internal.persistence.domain.evaluation.EvaluationPersistenceCallbacks

internal class ObserverEvaluationPersistenceCallbacks(
    private val compositeObserver: CompositeObserver
) : EvaluationPersistenceCallbacks {

    override fun onEvalStorageUpdated(evalKey: EvaluationKey, changeNumber: Long, evaluations: List<StoredEvaluation>) {
        compositeObserver.notifyEvent(ObservableEvent(ObservableEventType.EVAL_STORAGE_UPDATED))
    }

    override fun onLoadStarted() {
        compositeObserver.notifyEvent(ObservableEvent(ObservableEventType.EVAL_STORAGE_LOAD_STARTED))
    }

    override fun onLoadSucceeded(lastUpdateTimestamp: Long) {
        compositeObserver.notifyEvent(ObservableEvent(ObservableEventType.EVAL_STORAGE_LOAD_SUCCEEDED))
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
