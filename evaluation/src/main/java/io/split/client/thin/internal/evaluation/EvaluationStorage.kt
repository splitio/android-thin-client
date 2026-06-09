package io.split.client.thin.internal.evaluation

interface EvaluationReadStorage {
    fun get(flag: String, evalKey: EvaluationKey): StoredEvaluation?
    fun get(flags: Set<String>, evalKey: EvaluationKey): Map<String, StoredEvaluation>
    fun getByFlagSets(flagSets: Set<String>, evalKey: EvaluationKey): Map<String, StoredEvaluation>
    fun getFlagNames(evalKey: EvaluationKey): Set<String>
    fun getFlagNames(): Set<String>
    fun lastChangeNumber(evalKey: EvaluationKey): Long
    fun lastUpdateTimestamp(evalKey: EvaluationKey): Long?
}

interface PersistenceBackedStorage {
    suspend fun ensureCacheLoaded(evalKey: EvaluationKey)
}

data class UpsertResult(val updated: Boolean, val changedFlagNames: List<String>)

interface EvaluationWriteStorage {
    fun upsert(change: EvaluationChange): UpsertResult
    fun clear(evalKey: EvaluationKey)
}
