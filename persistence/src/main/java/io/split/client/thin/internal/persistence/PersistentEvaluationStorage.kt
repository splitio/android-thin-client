package io.split.client.thin.internal.persistence

data class SerializedEvaluation(
    val flagName: String,
    val json: String
)

data class PersistentEvaluationData(
    val changeNumber: Long,
    val evaluations: List<String>,
    val lastUpdateTimestamp: Long? = null
)

interface PersistentEvaluationStorage {
    fun loadForKey(keyHash: String, attrHash: String): PersistentEvaluationData?
    fun persistForKey(keyHash: String, attrHash: String, changeNumber: Long, evaluations: List<SerializedEvaluation>)
    fun clearForKey(keyHash: String)
    fun clearAll()
}
