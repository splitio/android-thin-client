package io.split.client.thin.internal.persistence

import io.split.client.thin.internal.evaluation.StoredEvaluation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RoomEvaluationPersistence(
    private val dao: EvaluationDao
) : PersistentEvaluationStorage {

    private val json = Json { ignoreUnknownKeys = true }

    override fun loadForKey(matchingKey: String): PersistentEvaluationData? {
        val entities = dao.getByKey(matchingKey)
        if (entities.isEmpty()) {
            return null
        }

        val changeNumber = entities.firstOrNull()?.changeNumber ?: return null
        val evaluations = entities.map { entity ->
            val dto = json.decodeFromString<StoredEvaluationDto>(entity.body)
            dto.toStoredEvaluation()
        }

        return PersistentEvaluationData(changeNumber, evaluations)
    }

    override fun persistForKey(
        matchingKey: String,
        changeNumber: Long,
        evaluations: List<StoredEvaluation>
    ) {
        val entities = evaluations.map { stored ->
            val dto = StoredEvaluationDto.fromStoredEvaluation(stored)
            EvaluationEntity(
                matchingKey,
                stored.result.flag,
                json.encodeToString(dto),
                changeNumber,
                System.currentTimeMillis()
            )
        }

        dao.replaceForKey(matchingKey, entities)
    }

    override fun clearForKey(matchingKey: String) {
        dao.deleteByKey(matchingKey)
    }
}
