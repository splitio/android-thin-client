package io.split.client.thin.internal.secure

data class EvaluationFilters(
    val sets: Set<String> = emptySet(),
    val configs: Boolean = false,
)
