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
    private val sdkKey: String,
    private val impressionsMode: Int? = null,
    private val sdkVersion: String = SDK_VERSION,
) : SecureHttpClient {

    override suspend fun fetchEvaluations(target: EvaluationTarget, filters: EvaluationFilters?): HttpResponse {
        val uri = buildEvaluationsUri(target, filters)
        val body = buildEvaluationsBody(target)
        val token = authProvider.credential(target).token
        val request = buildEvaluationsRequest(uri, body, token)
        val response = retryableHttpClient.execute(request, RequestCategory.EVALUATIONS)
        if (response.getHttpStatus() == HTTP_UNAUTHORIZED) {
            authProvider.invalidate(target)
            val freshToken = authProvider.credential(target).token
            val retryRequest = buildEvaluationsRequest(uri, body, freshToken)
            return retryableHttpClient.execute(retryRequest, RequestCategory.EVALUATIONS)
        }
        return response
    }

    override suspend fun postEvents(payload: String): HttpResponse {
        val request = buildRequest(URI(eventsUrl), HttpMethod.POST, payload, sdkKey)
        return retryableHttpClient.execute(request, RequestCategory.EVENTS)
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

    private fun buildEvaluationsRequest(uri: URI, body: String, token: String): HttpRequestDescriptor {
        return HttpRequestDescriptor(
            uri = uri,
            method = HttpMethod.POST,
            body = body,
            headers = mapOf(
                "Authorization" to "Bearer $token",
                "X-Harness-FME-SDK-Thin-Version" to "android-thin-$sdkVersion",
                "X-Harness-FME-SDK-Thin-Spec" to SDK_SPEC_VERSION,
            ),
        )
    }

    private fun buildEvaluationsUri(target: EvaluationTarget, filters: EvaluationFilters?): URI {
        val params = mutableListOf<String>()
        params.add("user=${encode(target.matchingKey)}")
        target.bucketingKey?.let { params.add("bucketingKey=${encode(it)}") }
        params.add("since=${filters?.changeNumber ?: -1}")
        filters?.flagNames?.forEach { params.add("flags=${encode(it)}") }
        filters?.flagSets?.forEach { params.add("sets=${encode(it)}") }
        filters?.withDynamicConfig?.let { params.add("withDynamicConfig=$it") }
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
