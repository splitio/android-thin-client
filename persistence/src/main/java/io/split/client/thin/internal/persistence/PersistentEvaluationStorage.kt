package io.split.client.thin.internal.persistence

data class SerializedEvaluation(
    val flagName: String,
    val json: String
)

data class PersistentEvaluationData(
    val changeNumber: Long,
    val evaluations: List<String>
)

interface PersistentEvaluationStorage {
    fun loadForKey(matchingKey: String): PersistentEvaluationData?
    fun persistForKey(matchingKey: String, changeNumber: Long, evaluations: List<SerializedEvaluation>)
    fun clearForKey(matchingKey: String)
}
