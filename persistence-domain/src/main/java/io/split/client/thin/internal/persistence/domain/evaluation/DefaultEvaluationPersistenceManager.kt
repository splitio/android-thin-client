package io.split.client.thin.internal.persistence.domain.evaluation

import io.split.client.thin.internal.evaluation.CacheLoadResult
import io.split.client.thin.internal.evaluation.EvaluationChange
import io.split.client.thin.internal.evaluation.EvaluationKey
import io.split.client.thin.internal.evaluation.StoredEvaluation
import io.split.client.thin.internal.persistence.PersistentEvaluationStorage
import io.split.client.thin.internal.persistence.SerializedEvaluation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class DefaultEvaluationPersistenceManager(
    private val persistentStorage: PersistentEvaluationStorage,
    private val callbacks: EvaluationPersistenceCallbacks,
    private val targetHasher: TargetHasher,
    private val evalSerializer: StoredEvaluationSerializer,
    private val scope: CoroutineScope
) : EvaluationPersistenceManager {

    override suspend fun loadLocal(evalKey: EvaluationKey): CacheLoadResult? {
        callbacks.onLoadStarted()
        return try {
            val hashed = targetHasher.hash(evalKey)
            val persistedData = persistentStorage.loadForKey(hashed.keyHash, hashed.attrsHash) ?: return null

            val evaluations = persistedData.evaluations.map { evalSerializer.deserialize(it) }
            val change = EvaluationChange(
                evaluationKey = evalKey,
                changeNumber = persistedData.changeNumber,
                evaluations = evaluations
            )

            callbacks.onEvalStorageUpdated(evalKey, persistedData.changeNumber, evaluations)
            val timestamp = persistedData.lastUpdateTimestamp ?: System.currentTimeMillis()
            callbacks.onLoadSucceeded(timestamp)

            CacheLoadResult(change, persistedData.lastUpdateTimestamp)
        } catch (e: Exception) {
            callbacks.onLoadFailed(e.message ?: "Unknown load error")
            null
        }
    }

    override fun persistAsync(
        evalKey: EvaluationKey,
        changeNumber: Long,
        evaluations: List<StoredEvaluation>
    ) {
        scope.launch {
            callbacks.onWriteScheduled()
            try {
                val hashed = targetHasher.hash(evalKey)
                val serializedEvals = evaluations.map { eval ->
                    SerializedEvaluation(flagName = eval.result.flag, json = evalSerializer.serialize(eval))
                }

                persistentStorage.persistForKey(hashed.keyHash, hashed.attrsHash, changeNumber, serializedEvals)


                callbacks.onWriteSucceeded()
            } catch (e: Exception) {
                callbacks.onWriteFailed(e.message ?: "Unknown persistence error")
            }
        }
    }

}
