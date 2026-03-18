package io.split.client.thin.internal.secure

import io.split.android.client.network.HttpMethod
import io.split.android.client.network.HttpResponse
import io.split.client.thin.http.HttpRequestDescriptor
import io.split.client.thin.http.RequestCategory
import io.split.client.thin.http.RetryableHttpClient
import io.split.client.thin.internal.auth.AuthProvider
import java.net.URI
import java.net.URLEncoder

internal class DefaultSecureHttpClient(
    private val authProvider: AuthProvider<EvaluationTarget>,
    private val retryableHttpClient: RetryableHttpClient,
    private val defaultTarget: EvaluationTarget,
    private val evaluationsUrl: String,
    private val eventsUrl: String,
    private val telemetryUrl: String,
    private val impressionsMode: Int? = null,
) : SecureHttpClient {

    override suspend fun fetchEvaluations(target: EvaluationTarget, filters: EvaluationFilters?): HttpResponse {
        val uri = buildEvaluationsUri(target, filters)
        val body = buildEvaluationsBody(target)
        return executeAuthenticated(target, uri, HttpMethod.POST, body, RequestCategory.EVALUATIONS)
    }

    override suspend fun postEvents(payload: String): HttpResponse {
        return executeAuthenticated(defaultTarget, URI(eventsUrl), HttpMethod.POST, payload, RequestCategory.EVENTS)
    }

    override suspend fun postTelemetry(payload: String): HttpResponse {
        return executeAuthenticated(defaultTarget, URI(telemetryUrl), HttpMethod.POST, payload, RequestCategory.TELEMETRY)
    }

    override suspend fun openStreaming(target: EvaluationTarget) {
        throw UnsupportedOperationException("Streaming not yet implemented")
    }

    override suspend fun closeStreaming() {
        throw UnsupportedOperationException("Streaming not yet implemented")
    }

    private suspend fun executeAuthenticated(
        target: EvaluationTarget,
        uri: URI,
        method: HttpMethod,
        body: String,
        category: RequestCategory,
    ): HttpResponse {
        val token = authProvider.credential(target).token
        val request = buildRequest(uri, method, body, token)
        val response = retryableHttpClient.execute(request, category)
        if (response.getHttpStatus() == HTTP_UNAUTHORIZED) {
            authProvider.invalidate(target)
            val freshToken = authProvider.credential(target).token
            val retryRequest = buildRequest(uri, method, body, freshToken)
            return retryableHttpClient.execute(retryRequest, category)
        }
        return response
    }

    private fun buildRequest(uri: URI, method: HttpMethod, body: String, token: String): HttpRequestDescriptor {
        return HttpRequestDescriptor(
            uri = uri,
            method = method,
            body = body,
            headers = mapOf("Authorization" to "Bearer $token"),
        )
    }

    private fun buildEvaluationsUri(target: EvaluationTarget, filters: EvaluationFilters?): URI {
        val params = mutableListOf<String>()
        params.add("user=${encode(target.matchingKey)}")
        params.add("changeNumber=${filters?.changeNumber ?: -1}")
        filters?.flagNames?.forEach { params.add("flags=${encode(it)}") }
        filters?.flagSets?.forEach { params.add("sets=${encode(it)}") }
        filters?.withConfig?.let { params.add("withConfig=$it") }
        impressionsMode?.let { params.add("impressionsMode=$it") }
        return URI("$evaluationsUrl?${params.joinToString("&")}")
    }

    private fun buildEvaluationsBody(target: EvaluationTarget): String {
        val attrs = target.attributes
        if (attrs.isNullOrEmpty()) return "{}"
        val sb = StringBuilder("{\"attributes\":{")
        attrs.entries.forEachIndexed { i, (k, v) ->
            if (i > 0) sb.append(",")
            sb.append("\"$k\":\"$v\"")
        }
        sb.append("}}")
        return sb.toString()
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
    }
}
