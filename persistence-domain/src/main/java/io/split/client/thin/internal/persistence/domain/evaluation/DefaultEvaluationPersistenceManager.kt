package io.split.client.thin.internal.persistence.domain.evaluation

import io.split.client.thin.internal.evaluation.EvaluationChange
import io.split.client.thin.internal.evaluation.EvaluationKey
import io.split.client.thin.internal.evaluation.StoredEvaluation
import io.split.client.thin.internal.persistence.AttributesDao
import io.split.client.thin.internal.persistence.AttributesEntity
import io.split.client.thin.internal.persistence.PersistentEvaluationStorage
import io.split.client.thin.internal.persistence.SerializedEvaluation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class DefaultEvaluationPersistenceManager(
    private val persistentStorage: PersistentEvaluationStorage,
    private val attributesDao: AttributesDao,
    private val callbacks: EvaluationPersistenceCallbacks,
    private val keySerializer: EvaluationKeySerializer,
    private val attributesSerializer: AttributesSerializer,
    private val evalSerializer: StoredEvaluationSerializer,
    private val scope: CoroutineScope
) : EvaluationPersistenceManager {

    override suspend fun loadLocal(evalKey: EvaluationKey): EvaluationChange? {
        callbacks.onLoadStarted()
        return try {
            val keyString = keySerializer.serialize(evalKey.key)
            val persistedData = persistentStorage.loadForKey(keyString) ?: return null

            val attrsEntity = attributesDao.getByKey(keyString)
            val attributes = if (attrsEntity != null) {
                attributesSerializer.deserialize(attrsEntity.json)
            } else {
                emptyMap()
            }

            val fullEvalKey = EvaluationKey(key = evalKey.key, attributes = attributes)
            val evaluations = persistedData.evaluations.map { evalSerializer.deserialize(it) }
            val change = EvaluationChange(
                evaluationKey = fullEvalKey,
                changeNumber = persistedData.changeNumber,
                evaluations = evaluations
            )

            callbacks.onEvalStorageUpdated(fullEvalKey, persistedData.changeNumber, evaluations)
            callbacks.onLoadSucceeded(attrsEntity?.updatedAt ?: System.currentTimeMillis())

            change
        } catch (e: Exception) {
            callbacks.onLoadFailed(e.message ?: "Unknown load error")
            null
        }
    }

    internal fun persistAsync(
        evalKey: EvaluationKey,
        changeNumber: Long,
        evaluations: List<StoredEvaluation>
    ) {
        scope.launch {
            callbacks.onWriteScheduled()
            try {
                val keyString = keySerializer.serialize(evalKey.key)
                val attributesJson = attributesSerializer.serialize(evalKey.attributes)
                val serializedEvals = evaluations.map { eval ->
                    SerializedEvaluation(flagName = eval.result.flag, json = evalSerializer.serialize(eval))
                }

                persistentStorage.persistForKey(keyString, changeNumber, serializedEvals)
                attributesDao.insert(
                    AttributesEntity(keyString, attributesJson, System.currentTimeMillis())
                )

                callbacks.onWriteSucceeded()
            } catch (e: Exception) {
                callbacks.onWriteFailed(e.message ?: "Unknown persistence error")
            }
        }
    }

    override fun start() { /* no-op for now */ }
    override fun stop() { /* no-op for now */ }
}
