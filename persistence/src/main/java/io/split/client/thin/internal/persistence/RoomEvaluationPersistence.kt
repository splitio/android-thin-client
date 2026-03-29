package io.split.client.thin.internal.persistence

class RoomEvaluationPersistence(
    private val evaluationDao: EvaluationDao,
    private val metadataDao: EvaluationMetadataDao
) : PersistentEvaluationStorage {

    override fun loadForKey(key: String): PersistentEvaluationData? {
        val metadata = metadataDao.getByKey(key) ?: return null
        val entities = evaluationDao.getByKey(key)

        if (entities.isEmpty()) {
            metadataDao.deleteByKey(key)
            return null
        }

        val evaluationJsons = entities.map { it.body }
        return PersistentEvaluationData(metadata.changeNumber, evaluationJsons)
    }

    override fun persistForKey(
        key: String,
        changeNumber: Long,
        evaluations: List<SerializedEvaluation>
    ) {
        if (evaluations.isEmpty()) {
            metadataDao.deleteByKey(key)
            evaluationDao.deleteByKey(key)
            return
        }

        metadataDao.insert(
            EvaluationMetadataEntity(
                key,
                changeNumber,
                System.currentTimeMillis()
            )
        )

        val entities = evaluations.map { serialized ->
            EvaluationEntity(
                key,
                serialized.flagName,
                serialized.json,
                System.currentTimeMillis()
            )
        }

        evaluationDao.replaceForKey(key, entities)
    }

    override fun clearForKey(key: String) {
        metadataDao.deleteByKey(key)
        evaluationDao.deleteByKey(key)
    }
}
