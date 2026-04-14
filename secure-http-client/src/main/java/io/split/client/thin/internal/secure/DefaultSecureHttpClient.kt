package io.split.client.thin.internal.secure

import io.split.android.client.network.HttpMethod
import io.split.android.client.network.HttpResponse
import io.split.client.thin.http.HttpRequestDescriptor
import io.split.client.thin.http.RequestCategory
import io.split.client.thin.http.RetryableHttpClient
import io.split.client.thin.internal.auth.AuthProvider
import io.split.client.thin.internal.auth.JwtCredential
import java.net.URI
import java.net.URLEncoder

internal class DefaultSecureHttpClient(
    private val authProvider: AuthProvider,
    private val retryableHttpClient: RetryableHttpClient,
    private val evaluationsUrl: String,
    private val eventsUrl: String,
    private val telemetryUrl: String,
    private val sdkKey: String,
    private val impressionsMode: Int? = null,
    private val sdkVersion: String = SDK_VERSION,
) : SecureHttpClient {

    override suspend fun fetchEvaluations(target: EvaluationTarget, filters: EvaluationFilters?, changeNumber: Long): HttpResponse {
        val uri = buildEvaluationsUri(target, filters, changeNumber)
        val body = buildEvaluationsBody(target)
        val digest = ContentDigest.compute(target)
        val token = authProvider.credential().token
        val request = buildEvaluationsRequest(uri, body, token, digest)
        val response = retryableHttpClient.execute(request, RequestCategory.EVALUATIONS)
        if (response.getHttpStatus() == HTTP_UNAUTHORIZED) {
            authProvider.invalidateAll()
            val freshToken = authProvider.credential().token
            val retryRequest = buildEvaluationsRequest(uri, body, freshToken, digest)
            return retryableHttpClient.execute(retryRequest, RequestCategory.EVALUATIONS)
        }
        return response
    }

    override suspend fun postEvents(payload: String): HttpResponse {
        val request = buildRequest(URI(eventsUrl), HttpMethod.POST, payload, sdkKey)
        return retryableHttpClient.execute(request, RequestCategory.EVENTS)
    }

    override suspend fun postTelemetry(payload: String): HttpResponse {
        return executeAuthenticated(URI(telemetryUrl), HttpMethod.POST, payload, RequestCategory.TELEMETRY)
    }

    private suspend fun executeAuthenticated(
        uri: URI,
        method: HttpMethod,
        body: String,
        category: RequestCategory,
    ): HttpResponse {
        val token = authProvider.credential().token
        val request = buildRequest(uri, method, body, token)
        val response = retryableHttpClient.execute(request, category)
        if (response.getHttpStatus() == HTTP_UNAUTHORIZED) {
            authProvider.invalidateAll()
            val freshToken = authProvider.credential().token
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
            headers = mapOf(
                "Authorization" to "Bearer $token",
                "Content-Type" to "application/json",
                "Accept" to "application/json",
                "SplitSDKVersion" to "android_thin-$sdkVersion",
                "X-Harness-FME-SDK-Thin-Version" to "android_thin-$sdkVersion",
            ),
        )
    }

    private fun buildEvaluationsRequest(uri: URI, body: String, token: String, digest: String): HttpRequestDescriptor {
        return HttpRequestDescriptor(
            uri = uri,
            method = HttpMethod.POST,
            body = body,
            headers = mapOf(
                "Authorization" to "Bearer $token",
                "X-Harness-FME-SDK-Thin-Version" to "android_thin-$sdkVersion",
                "X-Harness-FME-SDK-Thin-Spec" to SDK_SPEC_VERSION,
                "X-Harness-FME-Content-Digest" to digest,
            ),
        )
    }

    private fun buildEvaluationsUri(target: EvaluationTarget, filters: EvaluationFilters?, changeNumber: Long): URI {
        val params = mutableListOf<String>()
        params.add("user=${encode(target.matchingKey)}")
        target.bucketingKey?.let { params.add("bucketingKey=${encode(it)}") }
        params.add("since=$changeNumber")
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
