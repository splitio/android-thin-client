package io.split.client.thin.internal.persistence

import io.split.client.thin.internal.evaluation.StoredEvaluation

data class PersistentEvaluationData(
    val changeNumber: Long,
    val evaluations: List<StoredEvaluation>
)

interface PersistentEvaluationStorage {
    fun loadForKey(matchingKey: String): PersistentEvaluationData?
    fun persistForKey(matchingKey: String, changeNumber: Long, evaluations: List<StoredEvaluation>)
    fun clearForKey(matchingKey: String)
}
