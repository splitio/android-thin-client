package io.split.client.thin.internal.persistence

import org.json.JSONObject

class RoomEvaluationPersistence(
    private val database: ThinClientDatabase
) : PersistentEvaluationStorage {

    private val evaluationDao get() = database.evaluationDao()
    private val generalInfoDao get() = database.generalInfoDao()

    override fun loadForKey(key: String): PersistentEvaluationData? {
        var result: PersistentEvaluationData? = null
        database.runInTransaction {
            val info = generalInfoDao.getByKey(key) ?: return@runInTransaction
            val entities = evaluationDao.getByKey(key)

            if (entities.isEmpty()) {
                generalInfoDao.deleteByKey(key)
                return@runInTransaction
            }

            val changeNumber = JSONObject(info.value).getLong(FIELD_CHANGE_NUMBER)
            result = PersistentEvaluationData(changeNumber, entities.map { it.body })
        }
        return result
    }

    override fun persistForKey(
        key: String,
        changeNumber: Long,
        evaluations: List<SerializedEvaluation>
    ) {
        database.runInTransaction {
            if (evaluations.isEmpty()) {
                generalInfoDao.deleteByKey(key)
                evaluationDao.deleteByKey(key)
                return@runInTransaction
            }

            val value = JSONObject()
                .put(FIELD_CHANGE_NUMBER, changeNumber)
                .put(FIELD_UPDATED_AT, System.currentTimeMillis())
                .toString()
            generalInfoDao.insert(GeneralInfoEntity(key, value))

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
    }

    override fun clearForKey(key: String) {
        database.runInTransaction {
            generalInfoDao.deleteByKey(key)
            evaluationDao.deleteByKey(key)
        }
    }

    private companion object {
        const val FIELD_CHANGE_NUMBER = "changeNumber"
        const val FIELD_UPDATED_AT = "updatedAt"
    }
}
