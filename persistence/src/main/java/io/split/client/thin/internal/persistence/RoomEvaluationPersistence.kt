package io.split.client.thin.internal.persistence

class RoomEvaluationPersistence(
    private val database: ThinClientDatabase
) : PersistentEvaluationStorage {

    private val evaluationDao get() = database.evaluationDao()
    private val attributesDao get() = database.attributesDao()

    override fun loadForKey(keyHash: String, attrHash: String): PersistentEvaluationData? {
        var result: PersistentEvaluationData? = null
        database.runInTransaction {
            val attrs = attributesDao.getByKey(keyHash) ?: return@runInTransaction
            if (attrs.attrHash != attrHash) return@runInTransaction
            val entities = evaluationDao.getByKey(keyHash)

            if (entities.isEmpty()) {
                attributesDao.deleteByKey(keyHash)
                return@runInTransaction
            }

            result = PersistentEvaluationData(attrs.changeNumber, entities.map { it.evalJson }, attrs.lastUpdateTimestamp)
        }
        return result
    }

    override fun persistForKey(
        keyHash: String,
        attrHash: String,
        changeNumber: Long,
        evaluations: List<SerializedEvaluation>
    ) {
        database.runInTransaction {
            if (evaluations.isEmpty()) {
                attributesDao.deleteByKey(keyHash)
                evaluationDao.deleteByKeyHash(keyHash)
                return@runInTransaction
            }

            attributesDao.insert(AttributesEntity(keyHash, attrHash, changeNumber, System.currentTimeMillis()))

            val entities = evaluations.map { serialized ->
                EvaluationEntity(keyHash, serialized.flagName, serialized.json)
            }
            evaluationDao.deleteByKeyHash(keyHash)
            evaluationDao.insert(entities)
        }
    }

    override fun clearForKey(keyHash: String) {
        database.runInTransaction {
            attributesDao.deleteByKey(keyHash)
            evaluationDao.deleteByKeyHash(keyHash)
        }
    }

    override fun clearAll() {
        database.runInTransaction {
            attributesDao.deleteAll()
            evaluationDao.deleteAll()
        }
    }
}
