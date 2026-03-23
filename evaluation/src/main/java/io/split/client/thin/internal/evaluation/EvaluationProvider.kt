package io.split.client.thin.internal.evaluation

import io.split.client.thin.internal.secure.EvaluationFilters
import io.split.client.thin.internal.secure.EvaluationTarget
import io.split.client.thin.internal.secure.SecureHttpClient

interface EvaluationProvider {
    suspend fun fetch(evalKey: EvaluationKey, filters: EvaluationFilters?): EvaluationChange
}

fun EvaluationKey.toEvaluationTarget() = EvaluationTarget(
    matchingKey = key.matchingKey,
    bucketingKey = key.bucketingKey,
    attributes = attributes.ifEmpty { null },
)

class DefaultEvaluationProvider(
    private val secureHttpClient: SecureHttpClient,
    private val deserializer: EvaluationResponseDeserializer,
) : EvaluationProvider {

    override suspend fun fetch(evalKey: EvaluationKey, filters: EvaluationFilters?): EvaluationChange {
        val target = evalKey.toEvaluationTarget()
        val response = secureHttpClient.fetchEvaluations(target, filters)
        val body = response.getData()
        check(!body.isNullOrEmpty()) { "Empty or null response body from evaluations endpoint" }
        return deserializer.deserialize(body, evalKey)
    }
}
