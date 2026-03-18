package io.split.client.thin.internal.secure

import io.split.android.client.network.HttpResponse

internal interface SecureHttpClient {
    suspend fun fetchEvaluations(target: EvaluationTarget, filters: EvaluationFilters?): HttpResponse
    suspend fun postEvents(payload: String): HttpResponse
    suspend fun postTelemetry(payload: String): HttpResponse
    suspend fun openStreaming(target: EvaluationTarget)
    suspend fun closeStreaming()
}
