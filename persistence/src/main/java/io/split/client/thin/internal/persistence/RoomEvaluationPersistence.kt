package io.split.client.thin.internal.persistence

import org.json.JSONObject

internal class RoomEvaluationPersistence(
    private val evaluationDao: EvaluationDao,
    private val generalInfoDao: GeneralInfoDao
) : PersistentEvaluationStorage {

    override fun loadForKey(key: String): PersistentEvaluationData? {
        val info = generalInfoDao.getByKey(key) ?: return null
        val entities = evaluationDao.getByKey(key)

        if (entities.isEmpty()) {
            generalInfoDao.deleteByKey(key)
            return null
        }

        val changeNumber = JSONObject(info.value).getLong(FIELD_CHANGE_NUMBER)
        val evaluationJsons = entities.map { it.body }
        return PersistentEvaluationData(changeNumber, evaluationJsons)
    }

    override fun persistForKey(
        key: String,
        changeNumber: Long,
        evaluations: List<SerializedEvaluation>
    ) {
        if (evaluations.isEmpty()) {
            generalInfoDao.deleteByKey(key)
            evaluationDao.deleteByKey(key)
            return
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

    override fun clearForKey(key: String) {
        generalInfoDao.deleteByKey(key)
        evaluationDao.deleteByKey(key)
    }

    private companion object {
        const val FIELD_CHANGE_NUMBER = "changeNumber"
        const val FIELD_UPDATED_AT = "updatedAt"
    }
}
