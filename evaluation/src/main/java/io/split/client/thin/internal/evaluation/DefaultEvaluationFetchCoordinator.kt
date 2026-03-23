package io.split.client.thin.internal.evaluation

import io.split.client.thin.internal.secure.EvaluationFilters
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

class DefaultEvaluationFetchCoordinator(
    private val provider: EvaluationProvider,
    private val readStorage: EvaluationReadStorage,
    private val writeStorage: EvaluationWriteStorage,
    private val onEvaluationsUpdated: (FetchReason) -> Unit = {},
    private val onEvalFetchRequested: (evalKey: EvaluationKey, reason: FetchReason) -> Unit = { _, _ -> },
    private val onEvalFetchDeduped: (evalKey: EvaluationKey) -> Unit = {},
    private val onEvalFetchSucceeded: (evalKey: EvaluationKey) -> Unit = {},
    private val onEvalFetchFailed: (evalKey: EvaluationKey, error: Throwable) -> Unit = { _, _ -> },
) : EvaluationFetchCoordinator {

    private val pending = ConcurrentHashMap<EvaluationKey, CompletableDeferred<Unit>>()
    private val fetchedKeys = ConcurrentHashMap.newKeySet<EvaluationKey>()

    override suspend fun fetchIfNeeded(evalKey: EvaluationKey, filters: EvaluationFilters?, reason: FetchReason): Boolean {
        onEvalFetchRequested(evalKey, reason)
        val deferred = CompletableDeferred<Unit>()
        val existing = pending.putIfAbsent(evalKey, deferred)
        if (existing != null) {
            onEvalFetchDeduped(evalKey)
            return false
        }
        try {
            val changeNumber = readStorage.lastChangeNumber(evalKey)
            val enrichedFilters = filters?.copy(changeNumber = changeNumber)
                ?: EvaluationFilters(flagNames = null, flagSets = null, changeNumber = changeNumber)
            val change = provider.fetch(evalKey, enrichedFilters)
            val updated = writeStorage.upsert(change)
            val isFirstFetch = fetchedKeys.add(evalKey)
            onEvalFetchSucceeded(evalKey)
            if (isFirstFetch || updated) onEvaluationsUpdated(reason)
            deferred.complete(Unit)
            return true
        } catch (t: Throwable) {
            onEvalFetchFailed(evalKey, t)
            deferred.completeExceptionally(t)
            throw t
        } finally {
            pending.remove(evalKey, deferred)
        }
    }

    override suspend fun awaitPending(evalKey: EvaluationKey) {
        pending[evalKey]?.await()
    }
}
