package io.split.client.thin.internal.persistence

class RoomEvaluationPersistence(
    private val evaluationDao: EvaluationDao,
    private val metadataDao: EvaluationMetadataDao
) : PersistentEvaluationStorage {

    override fun loadForKey(matchingKey: String): PersistentEvaluationData? {
        val metadata = metadataDao.getByKey(matchingKey) ?: return null
        val entities = evaluationDao.getByKey(matchingKey)

        if (entities.isEmpty()) {
            metadataDao.deleteByKey(matchingKey)
            return null
        }

        val evaluationJsons = entities.map { it.body }
        return PersistentEvaluationData(metadata.changeNumber, evaluationJsons)
    }

    override fun persistForKey(
        matchingKey: String,
        changeNumber: Long,
        evaluations: List<SerializedEvaluation>
    ) {
        if (evaluations.isEmpty()) {
            metadataDao.deleteByKey(matchingKey)
            evaluationDao.deleteByKey(matchingKey)
            return
        }

        metadataDao.insert(
            EvaluationMetadataEntity(
                matchingKey,
                changeNumber,
                System.currentTimeMillis()
            )
        )

        val entities = evaluations.map { serialized ->
            EvaluationEntity(
                matchingKey,
                serialized.flagName,
                serialized.json,
                System.currentTimeMillis()
            )
        }

        evaluationDao.replaceForKey(matchingKey, entities)
    }

    override fun clearForKey(matchingKey: String) {
        metadataDao.deleteByKey(matchingKey)
        evaluationDao.deleteByKey(matchingKey)
    }
}
