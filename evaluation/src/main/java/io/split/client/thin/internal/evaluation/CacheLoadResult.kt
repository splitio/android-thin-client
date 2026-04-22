package io.split.client.thin.internal.evaluation

data class CacheLoadResult(
    val change: EvaluationChange,
    val lastUpdateTimestamp: Long?
)
