package io.split.client.thin.internal.evaluation

import io.split.client.thin.EvaluationResult
import kotlinx.serialization.json.Json

fun interface EvaluationResponseDeserializer {
    fun deserialize(body: String, evalKey: EvaluationKey): EvaluationChange
}

class JsonEvaluationResponseDeserializer : EvaluationResponseDeserializer {

    private val json = Json { ignoreUnknownKeys = true }

    override fun deserialize(body: String, evalKey: EvaluationKey): EvaluationChange {
        val dto = json.decodeFromString<EvaluationsResponseDto>(body)
        val evaluations = dto.evaluations.map { evalDto ->
            StoredEvaluation(
                result = EvaluationResult(
                    flag = evalDto.flag,
                    treatment = evalDto.treatment,
                    config = evalDto.config,
                    changeNumber = evalDto.changeNumber,
                ),
                flagSets = evalDto.sets.toSet(),
            )
        }
        return EvaluationChange(
            evaluationKey = evalKey,
            changeNumber = dto.till,
            evaluations = evaluations,
        )
    }
}
