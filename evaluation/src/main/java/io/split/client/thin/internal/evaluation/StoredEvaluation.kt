package io.split.client.thin.internal.evaluation

import io.split.client.thin.EvaluationResult

data class StoredEvaluation(
    val result: EvaluationResult,
    val flagSets: Set<String> = emptySet(),
)
