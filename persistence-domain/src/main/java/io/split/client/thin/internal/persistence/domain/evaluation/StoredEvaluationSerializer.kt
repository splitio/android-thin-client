package io.split.client.thin.internal.persistence.domain.evaluation

import io.split.client.thin.EvaluationResult
import io.split.client.thin.internal.evaluation.StoredEvaluation
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class EvaluationResultDto(
    val flag: String,
    val treatment: String,
    val label: String? = null,
    val changeNumber: Long? = null,
    val config: String? = null
)

@Serializable
private data class StoredEvaluationDto(
    val result: EvaluationResultDto,
    val flagSets: List<String>
)

internal class StoredEvaluationSerializer(private val cipher: Any? = null) {

    private val json = Json { ignoreUnknownKeys = true }

    fun serialize(evaluation: StoredEvaluation): String {
        val dto = StoredEvaluationDto(
            result = EvaluationResultDto(
                flag = evaluation.result.flag,
                treatment = evaluation.result.treatment,
                label = evaluation.result.label,
                changeNumber = evaluation.result.changeNumber,
                config = evaluation.result.config
            ),
            flagSets = evaluation.flagSets.toList()
        )
        return json.encodeToString(dto)
    }

    fun deserialize(serialized: String): StoredEvaluation {
        val dto = json.decodeFromString<StoredEvaluationDto>(serialized)
        return StoredEvaluation(
            result = EvaluationResult(
                flag = dto.result.flag,
                treatment = dto.result.treatment,
                label = dto.result.label,
                changeNumber = dto.result.changeNumber,
                config = dto.result.config
            ),
            flagSets = dto.flagSets.toSet()
        )
    }
}
