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
    fun loadForKey(key: String): PersistentEvaluationData?
    fun persistForKey(key: String, changeNumber: Long, evaluations: List<SerializedEvaluation>)
    fun clearForKey(key: String)
}
