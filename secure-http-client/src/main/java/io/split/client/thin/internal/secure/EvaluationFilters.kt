package io.split.client.thin.internal.secure

data class EvaluationFilters(
    val flagNames: Set<String>?,
    val flagSets: Set<String>?,
    val withDynamicConfig: Boolean? = null,
)
