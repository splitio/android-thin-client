package io.split.client.thin.internal.evaluation

data class EvaluationChange(
    val evaluationKey: EvaluationKey,
    val changeNumber: Long,
    val evaluations: List<StoredEvaluation>,
)
