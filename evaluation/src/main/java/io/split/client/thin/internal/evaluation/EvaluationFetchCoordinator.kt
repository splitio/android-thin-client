package io.split.client.thin.internal.evaluation

import io.split.client.thin.internal.secure.EvaluationFilters

interface EvaluationKeyRegistry {
    fun fetchedKeys(): Set<EvaluationKey>
}

interface EvaluationFetchCoordinator : EvaluationKeyRegistry {
    suspend fun fetchIfNeeded(evalKey: EvaluationKey, filters: EvaluationFilters, reason: FetchReason, delayMs: Long = 0, targetChangeNumber: Long? = null): Boolean
    suspend fun refetchAll(
        filters: EvaluationFilters,
        reason: FetchReason,
        delayProvider: ((EvaluationKey) -> Long)? = null,
        keyFilter: (EvaluationKey) -> Boolean = { true },
    )
    fun forget(evalKey: EvaluationKey)
}
