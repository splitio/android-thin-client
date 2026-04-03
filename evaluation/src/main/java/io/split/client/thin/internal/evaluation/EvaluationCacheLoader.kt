package io.split.client.thin.internal.evaluation

interface EvaluationCacheLoader {
    suspend fun loadLocal(evalKey: EvaluationKey): EvaluationChange?
    fun persistAsync(evalKey: EvaluationKey, changeNumber: Long, evaluations: List<StoredEvaluation>)
}
