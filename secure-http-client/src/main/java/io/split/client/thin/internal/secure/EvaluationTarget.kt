package io.split.client.thin.internal.secure

data class EvaluationTarget(
    val matchingKey: String,
    val bucketingKey: String?,
    val attributes: Map<String, Any?>?,
)
