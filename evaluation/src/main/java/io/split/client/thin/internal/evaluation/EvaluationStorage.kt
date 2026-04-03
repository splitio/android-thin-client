package io.split.client.thin.internal.evaluation

interface EvaluationReadStorage {
    fun get(flag: String, evalKey: EvaluationKey): StoredEvaluation?
    fun get(flags: Set<String>, evalKey: EvaluationKey): Map<String, StoredEvaluation>
    fun getByFlagSets(flagSets: Set<String>, evalKey: EvaluationKey): Map<String, StoredEvaluation>
    fun getFlagNames(evalKey: EvaluationKey): Set<String>
    fun lastChangeNumber(evalKey: EvaluationKey): Long
}

interface PersistenceBackedStorage {
    suspend fun ensureCacheLoaded(evalKey: EvaluationKey)
}

interface EvaluationWriteStorage {
    fun upsert(change: EvaluationChange): Boolean
    fun clear(evalKey: EvaluationKey)
}
