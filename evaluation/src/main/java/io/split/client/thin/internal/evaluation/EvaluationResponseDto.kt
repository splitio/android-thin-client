package io.split.client.thin.internal.evaluation

import kotlinx.serialization.Serializable

@Serializable
data class EvaluationsResponseDto(
    val till: Long = -1L,
    val since: Long = -1L,
    val evaluations: List<EvaluationDto> = emptyList(),
)

@Serializable
data class EvaluationDto(
    val featureName: String,
    val treatment: String,
    val sets: List<String> = emptyList(),
    val config: String? = null,
)
