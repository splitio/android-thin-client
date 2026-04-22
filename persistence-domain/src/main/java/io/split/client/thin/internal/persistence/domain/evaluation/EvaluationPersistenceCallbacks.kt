package io.split.client.thin.internal.persistence.domain.evaluation

import io.split.client.thin.internal.evaluation.EvaluationKey
import io.split.client.thin.internal.evaluation.StoredEvaluation

interface EvaluationPersistenceCallbacks {
    fun onEvalStorageUpdated(evalKey: EvaluationKey, changeNumber: Long, evaluations: List<StoredEvaluation>)
    fun onLoadStarted()
    fun onLoadSucceeded(lastUpdateTimestamp: Long)
    fun onLoadFailed(error: String)
    fun onWriteScheduled()
    fun onWriteSucceeded()
    fun onWriteFailed(error: String)
}
