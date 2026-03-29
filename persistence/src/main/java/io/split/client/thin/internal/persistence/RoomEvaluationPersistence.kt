package io.split.client.thin.internal.persistence

class RoomEvaluationPersistence(
    private val evaluationDao: EvaluationDao,
    private val metadataDao: EvaluationMetadataDao
) : PersistentEvaluationStorage {

    override fun loadForKey(evaluationKey: String): PersistentEvaluationData? {
        val metadata = metadataDao.getByKey(evaluationKey) ?: return null
        val entities = evaluationDao.getByKey(evaluationKey)

        if (entities.isEmpty()) {
            metadataDao.deleteByKey(evaluationKey)
            return null
        }

        val evaluationJsons = entities.map { it.body }
        return PersistentEvaluationData(metadata.changeNumber, evaluationJsons)
    }

    override fun persistForKey(
        evaluationKey: String,
        changeNumber: Long,
        evaluations: List<SerializedEvaluation>
    ) {
        if (evaluations.isEmpty()) {
            metadataDao.deleteByKey(evaluationKey)
            evaluationDao.deleteByKey(evaluationKey)
            return
        }

        metadataDao.insert(
            EvaluationMetadataEntity(
                evaluationKey,
                changeNumber,
                System.currentTimeMillis()
            )
        )

        val entities = evaluations.map { serialized ->
            EvaluationEntity(
                evaluationKey,
                serialized.flagName,
                serialized.json,
                System.currentTimeMillis()
            )
        }

        evaluationDao.replaceForKey(evaluationKey, entities)
    }

    override fun clearForKey(evaluationKey: String) {
        metadataDao.deleteByKey(evaluationKey)
        evaluationDao.deleteByKey(evaluationKey)
    }
}
