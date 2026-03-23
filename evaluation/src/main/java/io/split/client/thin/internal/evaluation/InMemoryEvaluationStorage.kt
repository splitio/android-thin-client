package io.split.client.thin.internal.evaluation

import java.util.concurrent.ConcurrentHashMap

class InMemoryEvaluationStorage : EvaluationReadStorage, EvaluationWriteStorage {

    private class KeyEvaluations {
        val evaluations = ConcurrentHashMap<String, StoredEvaluation>()
        @Volatile var changeNumber: Long = -1L
    }

    private val store = ConcurrentHashMap<EvaluationKey, KeyEvaluations>()

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

    override fun upsert(change: EvaluationChange): Boolean {
        val keyEvals = store.getOrPut(change.evaluationKey) { KeyEvaluations() }
        synchronized(keyEvals) {
            val incomingFlagNames = change.evaluations.map { it.result.flag }.toSet()
            val shouldUpdate = change.changeNumber > keyEvals.changeNumber ||
                    incomingFlagNames != keyEvals.evaluations.keys
            if (!shouldUpdate) return false
            keyEvals.evaluations.clear()
            change.evaluations.forEach { keyEvals.evaluations[it.result.flag] = it }
            keyEvals.changeNumber = change.changeNumber
            return true
        }
    }

    override fun clear(evalKey: EvaluationKey) {
        store.remove(evalKey)
    }
}
