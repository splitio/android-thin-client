package io.split.client.thin.internal.persistence

import org.json.JSONObject

class RoomEvaluationPersistence(
    private val database: ThinClientDatabase
) : PersistentEvaluationStorage {

    private val evaluationDao get() = database.evaluationDao()
    private val generalInfoDao get() = database.generalInfoDao()

    override fun loadForKey(keyHash: String, attrsHash: String): PersistentEvaluationData? {
        var result: PersistentEvaluationData? = null
        database.runInTransaction {
            val info = generalInfoDao.getByKeyAndAttrs(keyHash, attrsHash) ?: return@runInTransaction
            val entities = evaluationDao.getByKeyAndAttrs(keyHash, attrsHash)

            if (entities.isEmpty()) {
                generalInfoDao.deleteByKeyAndAttrs(keyHash, attrsHash)
                return@runInTransaction
            }

            val changeNumber = JSONObject(info.value).getLong(FIELD_CHANGE_NUMBER)
            result = PersistentEvaluationData(changeNumber, entities.map { it.body })
        }
        return result
    }

    override fun persistForKey(
        keyHash: String,
        attrsHash: String,
        changeNumber: Long,
        evaluations: List<SerializedEvaluation>
    ) {
        database.runInTransaction {
            if (evaluations.isEmpty()) {
                generalInfoDao.deleteByKeyAndAttrs(keyHash, attrsHash)
                evaluationDao.deleteByKeyAndAttrs(keyHash, attrsHash)
                return@runInTransaction
            }

            val value = JSONObject()
                .put(FIELD_CHANGE_NUMBER, changeNumber)
                .put(FIELD_UPDATED_AT, System.currentTimeMillis())
                .toString()
            generalInfoDao.insert(GeneralInfoEntity(keyHash, attrsHash, value))

            val entities = evaluations.map { serialized ->
                EvaluationEntity(
                    keyHash,
                    serialized.flagName,
                    attrsHash,
                    serialized.json,
                    System.currentTimeMillis()
                )
            }
            evaluationDao.replaceForKeyAndAttrs(keyHash, attrsHash, entities)
        }
    }

    override fun clearForKey(keyHash: String) {
        database.runInTransaction {
            generalInfoDao.deleteByKeyHash(keyHash)
            evaluationDao.deleteByKeyHash(keyHash)
        }
    }

    private companion object {
        const val FIELD_CHANGE_NUMBER = "changeNumber"
        const val FIELD_UPDATED_AT = "updatedAt"
    }
}
