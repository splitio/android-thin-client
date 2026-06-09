package io.split.client.thin.internal.evaluation

import io.split.client.thin.internal.secure.EvaluationFilters
import io.split.client.thin.internal.secure.EvaluationTarget
import io.split.client.thin.internal.secure.SecureHttpClient

interface EvaluationProvider {
    suspend fun fetch(evalKey: EvaluationKey, filters: EvaluationFilters, changeNumber: Long, targetChangeNumber: Long? = null): EvaluationChange?
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
    private val onEmptyResponseBody: (evalKey: EvaluationKey) -> Unit = {},
) : EvaluationProvider {

    override suspend fun fetch(evalKey: EvaluationKey, filters: EvaluationFilters, changeNumber: Long, targetChangeNumber: Long?): EvaluationChange? {
        onEvalFetchStarted(evalKey)
        val target = evalKey.toEvaluationTarget()
        val response = secureHttpClient.fetchEvaluations(target, filters, changeNumber, targetChangeNumber)
        if (response.httpStatus == HTTP_NOT_MODIFIED) return null
        if (response.httpStatus == HTTP_UNAUTHORIZED) throw EvaluationAuthException("Evaluations fetch returned 401")
        val body = response.getData()
        if (body.isNullOrEmpty()) {
            onEmptyResponseBody(evalKey)
            return null
        }
        return try {
            deserializer.deserialize(body, evalKey)
        } catch (e: Exception) {
            onEvalDeserializeFailed(evalKey, e)
            throw e
        }
    }

    private companion object {
        private const val HTTP_NOT_MODIFIED = 304
        private const val HTTP_UNAUTHORIZED = 401
    }
}
