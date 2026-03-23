package io.split.client.thin.internal.evaluation

import io.split.client.thin.Target
import io.split.client.thin.internal.secure.EvaluationFilters

interface EvaluationRepository {
    fun getTreatment(evalKey: EvaluationKey, flag: String): StoredEvaluation?
    fun getTreatments(evalKey: EvaluationKey, flags: Set<String>): Map<String, StoredEvaluation>
    fun getTreatmentsByFlagSets(evalKey: EvaluationKey, flagSets: Set<String>): Map<String, StoredEvaluation>
    suspend fun setTarget(target: Target, filters: EvaluationFilters?)
    fun getFlagNames(evalKey: EvaluationKey): Set<String>
}

class DefaultEvaluationRepository(
    private val readStorage: EvaluationReadStorage,
    private val fetchCoordinator: EvaluationFetchCoordinator,
) : EvaluationRepository {

    override fun getTreatment(evalKey: EvaluationKey, flag: String): StoredEvaluation? {
        return readStorage.get(flag, evalKey)
    }

    override fun getTreatments(evalKey: EvaluationKey, flags: Set<String>): Map<String, StoredEvaluation> {
        return readStorage.get(flags, evalKey)
    }

    override fun getTreatmentsByFlagSets(evalKey: EvaluationKey, flagSets: Set<String>): Map<String, StoredEvaluation> {
        return readStorage.getByFlagSets(flagSets, evalKey)
    }

    override suspend fun setTarget(target: Target, filters: EvaluationFilters?) {
        val evalKey = target.toEvaluationKey()
        fetchCoordinator.fetchIfNeeded(evalKey, filters, FetchReason.TARGET_SWITCH)
    }

    override fun getFlagNames(evalKey: EvaluationKey): Set<String> {
        return readStorage.getFlagNames(evalKey)
    }
}
