package io.split.client.thin.internal.evaluation

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class InMemoryEvaluationStorage(
    private val cacheLoader: EvaluationCacheLoader? = null,
    private val onCacheLoaded: (evalKey: EvaluationKey) -> Unit = {},
) : EvaluationReadStorage, EvaluationWriteStorage, PersistenceBackedStorage {

    private class KeyEvaluations {
        @Volatile var evaluations: Map<String, StoredEvaluation> = emptyMap()
        @Volatile var changeNumber: Long = -1L
    }

    private val store = ConcurrentHashMap<EvaluationKey, KeyEvaluations>()
    private val loadedKeys: MutableSet<EvaluationKey> = Collections.newSetFromMap(ConcurrentHashMap())

    override suspend fun ensureCacheLoaded(evalKey: EvaluationKey) {
        if (!loadedKeys.add(evalKey)) return
        try {
            cacheLoader?.loadLocal(evalKey)?.let { cached ->
                upsert(cached)
                onCacheLoaded(evalKey)
            }
        } catch (e: Throwable) {
            loadedKeys.remove(evalKey)
            throw e
        }
    }

    override fun get(flag: String, evalKey: EvaluationKey): StoredEvaluation? {
        return store[evalKey]?.evaluations?.get(flag)
    }

    override fun get(flags: Set<String>, evalKey: EvaluationKey): Map<String, StoredEvaluation> {
        val keyEvals = store[evalKey]?.evaluations ?: return emptyMap()
        return flags.mapNotNull { flag -> keyEvals[flag]?.let { flag to it } }.toMap()
    }

    override fun getByFlagSets(flagSets: Set<String>, evalKey: EvaluationKey): Map<String, StoredEvaluation> {
        val keyEvals = store[evalKey]?.evaluations ?: return emptyMap()
        return keyEvals.filter { (_, stored) -> stored.flagSets.any { it in flagSets } }
    }

    override fun getFlagNames(evalKey: EvaluationKey): Set<String> {
        return store[evalKey]?.evaluations?.keys?.toSet() ?: emptySet()
    }

    override fun lastChangeNumber(evalKey: EvaluationKey): Long {
        return store[evalKey]?.changeNumber ?: -1L
    }

    override fun upsert(change: EvaluationChange): UpsertResult {
        val keyEvals = store.getOrPut(change.evaluationKey) { KeyEvaluations() }
        synchronized(keyEvals) {
            val incomingByFlag = change.evaluations.associateBy { it.result.flag }
            val incomingFlagNames = incomingByFlag.keys
            val shouldUpdate = change.changeNumber > keyEvals.changeNumber ||
                    incomingFlagNames != keyEvals.evaluations.keys
            if (!shouldUpdate) return UpsertResult(updated = false, emptyList())

            val changedFlagNames = LinkedHashSet<String>()
            for (flag in incomingFlagNames) {
                if (!keyEvals.evaluations.containsKey(flag)) changedFlagNames.add(flag)
            }
            for (flag in keyEvals.evaluations.keys) {
                if (!incomingFlagNames.contains(flag)) changedFlagNames.add(flag)
            }
            for (flag in incomingFlagNames) {
                val current = keyEvals.evaluations[flag] ?: continue
                val incoming = incomingByFlag[flag] ?: continue
                if (incoming.result.changeNumber != current.result.changeNumber) {
                    changedFlagNames.add(flag)
                }
            }

            keyEvals.evaluations = incomingByFlag
            keyEvals.changeNumber = change.changeNumber
            cacheLoader?.persistAsync(change.evaluationKey, change.changeNumber, change.evaluations)
            return UpsertResult(updated = true, changedFlagNames.toList())
        }
    }

    override fun clear(evalKey: EvaluationKey) {
        store.remove(evalKey)
    }
}
