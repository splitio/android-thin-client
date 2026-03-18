package io.split.client.thin.internal.secure

internal data class EvaluationFilters(
    val flagNames: Set<String>?,
    val flagSets: Set<String>?,
    val changeNumber: Long = -1,
    val withDynamicConfig: Boolean? = null,
)
