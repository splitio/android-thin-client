package io.split.client.thin.internal.secure

import io.split.android.client.network.HttpResponse
import io.split.client.thin.internal.auth.JwtCredential

interface SecureHttpClient {
    suspend fun fetchEvaluations(target: EvaluationTarget, filters: EvaluationFilters?): HttpResponse
    suspend fun postEvents(payload: String): HttpResponse
    suspend fun postTelemetry(payload: String): HttpResponse
    suspend fun openStreaming(target: EvaluationTarget)
    suspend fun closeStreaming(target: EvaluationTarget)
    suspend fun credentialForActiveTargets(): JwtCredential
}
