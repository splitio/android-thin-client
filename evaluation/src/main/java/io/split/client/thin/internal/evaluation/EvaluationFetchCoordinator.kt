package io.split.client.thin.internal.evaluation

import io.split.client.thin.internal.secure.EvaluationFilters

interface EvaluationFetchCoordinator {
    suspend fun fetchIfNeeded(evalKey: EvaluationKey, filters: EvaluationFilters?, reason: FetchReason): Boolean
    suspend fun awaitPending(evalKey: EvaluationKey)
}
