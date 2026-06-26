package io.split.client.thin.internal.secure

import io.split.client.thin.http.contracts.HttpResponse

interface SecureHttpClient {
    suspend fun fetchEvaluations(target: EvaluationTarget, request: EvaluationFilters, changeNumber: Long, targetChangeNumber: Long? = null): HttpResponse
    suspend fun postEvents(payload: String): HttpResponse
    suspend fun postTelemetry(payload: String): HttpResponse
}
