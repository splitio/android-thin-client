package io.split.client.thin.internal.secure

internal data class EvaluationTarget(
    val matchingKey: String,
    val bucketingKey: String?,
    val attributes: Map<String, Any?>?,
)
