package io.split.client.thin.internal.evaluation

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class InMemoryEvaluationStorage(
    private val cacheLoader: EvaluationCacheLoader? = null,
    private val onCacheLoaded: (evalKey: EvaluationKey, lastUpdateTimestamp: Long?) -> Unit = { _, _ -> },
) : EvaluationReadStorage, EvaluationWriteStorage, PersistenceBackedStorage {

    private class KeyEvaluations {
        @Volatile var evaluations: Map<String, StoredEvaluation> = emptyMap()
        @Volatile var changeNumber: Long = -1L
        @Volatile var lastUpdateTimestamp: Long? = null
    }

    private val store = ConcurrentHashMap<EvaluationKey, KeyEvaluations>()
    private val loadedKeys: MutableSet<EvaluationKey> = Collections.newSetFromMap(ConcurrentHashMap())

    override suspend fun ensureCacheLoaded(evalKey: EvaluationKey) {
        if (!loadedKeys.add(evalKey)) return
        try {
            cacheLoader?.loadLocal(evalKey)?.let { result ->
                upsert(result.change)
                result.lastUpdateTimestamp?.let { ts ->
                    store[evalKey]?.lastUpdateTimestamp = ts
                }
                onCacheLoaded(evalKey, result.lastUpdateTimestamp)
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

    override fun getFlagNames(): Set<String> {
        return store.values.firstOrNull()?.evaluations?.keys?.toSet() ?: emptySet()
    }

    override fun lastChangeNumber(evalKey: EvaluationKey): Long {
        return store[evalKey]?.changeNumber ?: -1L
    }

    override fun lastUpdateTimestamp(evalKey: EvaluationKey): Long? {
        return store[evalKey]?.lastUpdateTimestamp
    }

    /*
     * Upserts a full evaluation snapshot for one evaluation key.
     *
     * A newer top-level changeNumber replaces the current snapshot. A same-version snapshot is only accepted
     * when the flag membership changed, meaning the set of flag names is different. For example:
     *
     * - { "flag-a" } -> { "flag-a", "flag-b" } updates because "flag-b" was added.
     * - { "flag-a", "flag-b" } -> { "flag-a" } updates because "flag-b" was removed.
     * - { "flag-a" = "on" } -> { "flag-a" = "off" } with the same top-level changeNumber is ignored
     *   because the membership is unchanged; duplicate same-version payloads must not rewrite values.
     *
     * When a snapshot is accepted, changedFlagNames contains the flags whose observable data changed:
     * additions, removals, or content changes such as treatment, config, flag sets, or per-flag changeNumber.
     * That list is later used to build SDK_UPDATE metadata.
     */
    override fun upsert(change: EvaluationChange): UpsertResult {
        val keyEvals = store.getOrPut(change.evaluationKey) { KeyEvaluations() }
        synchronized(keyEvals) {
            // Normalize the incoming snapshot by flag name so we can diff it against the stored snapshot.
            val incomingByFlag = change.evaluations.associateBy { it.result.flag }
            val incomingFlagNames = incomingByFlag.keys

            // Drop stale or duplicate snapshots before doing any diff work.
            val shouldUpdate = change.changeNumber > keyEvals.changeNumber ||
                    (change.changeNumber >= keyEvals.changeNumber && incomingFlagNames != keyEvals.evaluations.keys)
            if (!shouldUpdate) {
                return UpsertResult(updated = false, emptyList())
            }

            val changedFlagNames = computeChangedFlagNames(keyEvals.evaluations, incomingByFlag)

            // Replace the snapshot atomically after diffing against the previous one.
            keyEvals.evaluations = incomingByFlag
            keyEvals.changeNumber = change.changeNumber

            // Persistence mirrors the in-memory snapshot after the update has been accepted.
            cacheLoader?.persistAsync(change.evaluationKey, change.changeNumber, change.evaluations)
            return UpsertResult(updated = true, changedFlagNames)
        }
    }

    override fun clear(evalKey: EvaluationKey) {
        store.remove(evalKey)
    }

    private fun computeChangedFlagNames(
        currentByFlag: Map<String, StoredEvaluation>,
        incomingByFlag: Map<String, StoredEvaluation>,
    ): List<String> {
        val changedFlagNames = LinkedHashSet<String>()

        // First include newly present flags.
        for (flag in incomingByFlag.keys) {
            if (!currentByFlag.containsKey(flag)) changedFlagNames.add(flag)
        }

        // Then include flags that disappeared from the latest snapshot.
        for (flag in currentByFlag.keys) {
            if (!incomingByFlag.containsKey(flag)) changedFlagNames.add(flag)
        }

        // Finally include flags that stayed present but changed treatment, config, sets, or version.
        for (flag in incomingByFlag.keys) {
            val current = currentByFlag[flag] ?: continue
            val incoming = incomingByFlag[flag] ?: continue
            if (storedEvaluationContentChanged(current, incoming)) {
                changedFlagNames.add(flag)
            }
        }

        return changedFlagNames.toList()
    }

    private fun storedEvaluationContentChanged(current: StoredEvaluation, incoming: StoredEvaluation): Boolean {
        if (current.flagSets != incoming.flagSets) return true
        val c = current.result
        val i = incoming.result
        return c.treatment != i.treatment ||
            c.config != i.config ||
            c.changeNumber != i.changeNumber
    }
}
