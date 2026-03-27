package io.split.client.thin.internal.persistence

import io.split.client.thin.EvaluationResult
import io.split.client.thin.internal.evaluation.StoredEvaluation
import kotlinx.serialization.Serializable

@Serializable
data class EvaluationResultDto(
    val flag: String,
    val treatment: String,
    val config: String? = null,
    val label: String? = null,
    val changeNumber: Long? = null
)

@Serializable
data class StoredEvaluationDto(
    val result: EvaluationResultDto,
    val flagSets: Set<String> = emptySet()
) {
    fun toStoredEvaluation(): StoredEvaluation {
        return StoredEvaluation(
            result = EvaluationResult(
                flag = result.flag,
                treatment = result.treatment,
                config = result.config,
                label = result.label,
                changeNumber = result.changeNumber
            ),
            flagSets = flagSets
        )
    }

    companion object {
        fun fromStoredEvaluation(stored: StoredEvaluation): StoredEvaluationDto {
            return StoredEvaluationDto(
                result = EvaluationResultDto(
                    flag = stored.result.flag,
                    treatment = stored.result.treatment,
                    config = stored.result.config,
                    label = stored.result.label,
                    changeNumber = stored.result.changeNumber
                ),
                flagSets = stored.flagSets
            )
        }
    }
}
