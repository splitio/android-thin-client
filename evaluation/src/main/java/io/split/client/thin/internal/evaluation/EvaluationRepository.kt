package io.split.client.thin.internal.evaluation

import io.split.client.thin.Target
import io.split.client.thin.internal.secure.EvaluationFilters
import kotlin.coroutines.cancellation.CancellationException

interface EvaluationRepository {
    fun getTreatment(evalKey: EvaluationKey, flag: String): StoredEvaluation?
    fun getTreatments(evalKey: EvaluationKey, flags: Set<String>): Map<String, StoredEvaluation>
    fun getTreatmentsByFlagSets(evalKey: EvaluationKey, flagSets: Set<String>): Map<String, StoredEvaluation>
    suspend fun setTarget(target: Target, filters: EvaluationFilters, isInitialization: Boolean = false)
    fun getFlagNames(evalKey: EvaluationKey): Set<String>
    fun getFlagNames(): Set<String>
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

    override suspend fun setTarget(target: Target, filters: EvaluationFilters, isInitialization: Boolean) {
        val evalKey = target.toEvaluationKey()
        try {
            persistenceBackedStorage?.ensureCacheLoaded(evalKey)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // observer already saw onLoadFailed
        }
        val reason = if (isInitialization) FetchReason.INITIALIZATION else FetchReason.TARGET_SWITCH
        fetchCoordinator.fetchIfNeeded(evalKey, filters, reason)
    }

    override fun getFlagNames(evalKey: EvaluationKey): Set<String> {
        return readStorage.getFlagNames(evalKey)
    }

    override fun getFlagNames(): Set<String> {
        return readStorage.getFlagNames()
    }
}
