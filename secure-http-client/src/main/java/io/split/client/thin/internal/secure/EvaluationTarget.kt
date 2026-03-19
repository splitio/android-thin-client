package io.split.client.thin.internal.secure

import io.split.client.thin.internal.auth.AuthParamsProvider

data class EvaluationTarget(
    val matchingKey: String,
    val bucketingKey: String?,
    val attributes: Map<String, Any?>?,
) : AuthParamsProvider {
    override fun getUsers(): String = matchingKey
}
