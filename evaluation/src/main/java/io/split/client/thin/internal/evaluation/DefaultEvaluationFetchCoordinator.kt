package io.split.client.thin.internal.evaluation

import io.split.client.thin.internal.secure.EvaluationFilters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class DefaultEvaluationFetchCoordinator(
    private val provider: EvaluationProvider,
    private val readStorage: EvaluationReadStorage,
    private val writeStorage: EvaluationWriteStorage,
    private val onEvaluationsUpdated: (EvaluationKey, FetchReason) -> Unit = { _, _ -> },
    private val onEvalFetchRequested: (evalKey: EvaluationKey, reason: FetchReason, delayMs: Long) -> Unit = { _, _, _ -> },
    private val onEvalFetchDeduped: (evalKey: EvaluationKey) -> Unit = {},
    private val onEvalFetchSucceeded: (evalKey: EvaluationKey) -> Unit = {},
    private val onEvalFetchFailed: (evalKey: EvaluationKey, error: Throwable) -> Unit = { _, _ -> },
) : EvaluationFetchCoordinator {

    private val inFlight: MutableSet<EvaluationKey> = Collections.newSetFromMap(ConcurrentHashMap())
    private val fetchedKeys: MutableSet<EvaluationKey> = Collections.newSetFromMap(ConcurrentHashMap())

    override suspend fun fetchIfNeeded(evalKey: EvaluationKey, filters: EvaluationFilters?, reason: FetchReason, delayMs: Long): Boolean {
        onEvalFetchRequested(evalKey, reason, delayMs)
        if (!inFlight.add(evalKey)) {
            onEvalFetchDeduped(evalKey)
            return false
        }
        try {
            val changeNumber = readStorage.lastChangeNumber(evalKey)
            val change = provider.fetch(evalKey, filters, changeNumber)
            val isFirstFetch = fetchedKeys.add(evalKey)
            if (change != null) {
                val updated = writeStorage.upsert(change)
                if (isFirstFetch || updated) onEvaluationsUpdated(evalKey, reason)
            } else if (isFirstFetch) {
                onEvaluationsUpdated(evalKey, reason)
            }
            onEvalFetchSucceeded(evalKey)
            return true
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            onEvalFetchFailed(evalKey, t)
            return false
        } finally {
            inFlight.remove(evalKey)
        }
    }

    override suspend fun refetchAll(filters: EvaluationFilters?, reason: FetchReason, delayProvider: ((EvaluationKey) -> Long)?) {
        val snapshot = fetchedKeys.toSet()
        for (evalKey in snapshot) {
            try {
                val delayMs = delayProvider?.invoke(evalKey) ?: 0L
                if (delayMs > 0) delay(delayMs)
                fetchIfNeeded(evalKey, filters, reason, delayMs)
            } catch (e: Throwable) {
                // Silently continue - errors don't stop the batch
            }
        }
    }
}
