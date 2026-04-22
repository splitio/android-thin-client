package io.split.client.thin.internal.evaluation

import io.split.client.thin.Target
import io.split.client.thin.internal.secure.EvaluationFilters

interface EvaluationRepository {
    fun getTreatment(evalKey: EvaluationKey, flag: String): StoredEvaluation?
    fun getTreatments(evalKey: EvaluationKey, flags: Set<String>): Map<String, StoredEvaluation>
    fun getTreatmentsByFlagSets(evalKey: EvaluationKey, flagSets: Set<String>): Map<String, StoredEvaluation>
    suspend fun setTarget(target: Target, filters: EvaluationFilters?, isInitialization: Boolean = false)
    fun getFlagNames(evalKey: EvaluationKey): Set<String>
}

class DefaultEvaluationRepository(
    private val readStorage: EvaluationReadStorage,
    private val fetchCoordinator: EvaluationFetchCoordinator,
    private val persistenceBackedStorage: PersistenceBackedStorage? = null,
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

    override suspend fun setTarget(target: Target, filters: EvaluationFilters?, isInitialization: Boolean) {
        val evalKey = target.toEvaluationKey()
        persistenceBackedStorage?.ensureCacheLoaded(evalKey)
        val reason = if (isInitialization) FetchReason.INITIALIZATION else FetchReason.TARGET_SWITCH
        fetchCoordinator.fetchIfNeeded(evalKey, filters, reason)
    }

    override fun getFlagNames(evalKey: EvaluationKey): Set<String> {
        return readStorage.getFlagNames(evalKey)
    }
}
