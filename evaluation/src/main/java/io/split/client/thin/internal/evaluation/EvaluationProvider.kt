package io.split.client.thin.internal.evaluation

import io.split.client.thin.internal.secure.EvaluationFilters
import io.split.client.thin.internal.secure.EvaluationTarget
import io.split.client.thin.internal.secure.SecureHttpClient

interface EvaluationProvider {
    suspend fun fetch(evalKey: EvaluationKey, filters: EvaluationFilters?, changeNumber: Long): EvaluationChange
}

fun EvaluationKey.toEvaluationTarget() = EvaluationTarget(
    matchingKey = key.matchingKey,
    bucketingKey = key.bucketingKey,
    attributes = attributes.ifEmpty { null },
)

class DefaultEvaluationProvider(
    private val secureHttpClient: SecureHttpClient,
    private val deserializer: EvaluationResponseDeserializer,
    private val onEvalFetchStarted: (evalKey: EvaluationKey) -> Unit = {},
    private val onEvalDeserializeFailed: (evalKey: EvaluationKey, error: Exception) -> Unit = { _, _ -> },
) : EvaluationProvider {

    override suspend fun fetch(evalKey: EvaluationKey, filters: EvaluationFilters?, changeNumber: Long): EvaluationChange {
        onEvalFetchStarted(evalKey)
        val target = evalKey.toEvaluationTarget()
        val response = secureHttpClient.fetchEvaluations(target, filters, changeNumber)
        val body = response.getData()
        check(!body.isNullOrEmpty()) { "Empty or null response body from evaluations endpoint" }
        return try {
            deserializer.deserialize(body, evalKey)
        } catch (e: Exception) {
            onEvalDeserializeFailed(evalKey, e)
            throw e
        }
    }
}
