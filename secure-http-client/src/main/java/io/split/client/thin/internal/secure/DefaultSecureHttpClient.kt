package io.split.client.thin.internal.secure

import io.split.android.client.utils.logger.Logger
import io.split.client.thin.http.HttpRequestDescriptor
import io.split.client.thin.http.RequestCategory
import io.split.client.thin.http.RetryableHttpClient
import io.split.client.thin.http.contracts.HttpMethod
import io.split.client.thin.http.contracts.HttpResponse
import io.split.client.thin.internal.auth.AuthProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI
import java.net.URLEncoder

internal class DefaultSecureHttpClient(
    private val authProvider: AuthProvider,
    private val retryableHttpClient: RetryableHttpClient,
    private val evaluationsUrl: String,
    private val eventsUrl: String,
    private val telemetryUrl: String,
    private val sdkKey: String,
    private val sdkVersion: String = SDK_VERSION,
) : SecureHttpClient {

    override suspend fun fetchEvaluations(target: EvaluationTarget, request: EvaluationFilters, changeNumber: Long, targetChangeNumber: Long?): HttpResponse {
        val uri = buildEvaluationsUri(changeNumber, targetChangeNumber)
        val body = buildEvaluationsBody(target, request)
        val digest = ContentDigest.compute(body)
        val correlationId = newCorrelationId()
        val token = authProvider.credential().token
        val httpRequest = buildEvaluationsRequest(uri, body, token, digest)
        val response = executeLogged(correlationId, httpRequest, RequestCategory.EVALUATIONS)
        if (response.httpStatus == HTTP_UNAUTHORIZED) {
            authProvider.invalidateAll()
            val freshToken = authProvider.credential().token
            val retryRequest = buildEvaluationsRequest(uri, body, freshToken, digest)
            return executeLogged(correlationId, retryRequest, RequestCategory.EVALUATIONS)
        }
        return response
    }

    override suspend fun postEvents(payload: String): HttpResponse {
        val request = buildRequest(URI(eventsUrl), HttpMethod.POST, payload, sdkKey)
        return executeLogged(newCorrelationId(), request, RequestCategory.EVENTS)
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
        val correlationId = newCorrelationId()
        val token = authProvider.credential().token
        val request = buildRequest(uri, method, body, token)
        val response = executeLogged(correlationId, request, category)
        if (response.httpStatus == HTTP_UNAUTHORIZED) {
            authProvider.invalidateAll()
            val freshToken = authProvider.credential().token
            val retryRequest = buildRequest(uri, method, body, freshToken)
            return executeLogged(correlationId, retryRequest, category)
        }
        return response
    }

    private suspend fun executeLogged(
        correlationId: String,
        request: HttpRequestDescriptor,
        category: RequestCategory,
    ): HttpResponse {
        logRequest(correlationId, request)
        val response = retryableHttpClient.execute(request, category)
        logResponse(correlationId, response)
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
                "SplitSDKVersion" to "AndroidThin-$sdkVersion",
                "X-Harness-FME-SDK-Version" to "AndroidThin-$sdkVersion",
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
                "X-Harness-FME-SDK-Version" to "AndroidThin-$sdkVersion",
                "X-Harness-FME-Content-Digest" to digest,
            ),
        )
    }

    private fun buildEvaluationsUri(changeNumber: Long, targetChangeNumber: Long? = null): URI {
        val builder = QueryParamsBuilder()
        builder.add("since", changeNumber.toString())
        targetChangeNumber?.let { builder.add("till", it.toString()) }
        return URI("$evaluationsUrl?${builder.build()}")
    }

    private fun buildEvaluationsBody(target: EvaluationTarget, request: EvaluationFilters): String {
        val bodyMap = sortedMapOf(
            "attributes" to serializeAttributes(target.attributes),
            "bucketingKey" to (target.bucketingKey?.let { JsonPrimitive(it) } ?: JsonPrimitive(null as String?)),
            "configs" to JsonPrimitive(request.configs),
            "key" to JsonPrimitive(target.matchingKey),
            "sets" to JsonArray(request.sets.sorted().map { JsonPrimitive(it) }),
        )
        return Json.encodeToString(JsonObject(bodyMap))
    }

    private fun serializeAttributes(attributes: Map<String, Any?>?): JsonElement {
        val nonNull = attributes?.filterValues { it != null } ?: emptyMap()
        if (nonNull.isEmpty()) return JsonObject(emptyMap())
        val sorted = nonNull.keys.sorted().associate { key -> key to valueToJsonElement(nonNull[key]) }
        return JsonObject(sorted)
    }

    private fun valueToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonPrimitive(null as String?)
            is String -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is List<*> -> {
                val elements = value.filterNotNull().sortedBy { it.toString() }.map { valueToJsonElement(it) }
                JsonArray(elements)
            }
            else -> JsonPrimitive(value.toString())
        }
    }

    private inner class QueryParamsBuilder {
        private val params = mutableMapOf<String, String>()

        fun add(key: String, value: String) {
            params[key] = value
        }

        fun build(): String = params.entries
            .sortedBy { it.key }
            .joinToString("&") { "${it.key}=${it.value}" }
    }

    private fun newCorrelationId(): String = java.util.UUID.randomUUID().toString().take(8)

    private fun logRequest(correlationId: String, request: HttpRequestDescriptor) {
        Logger.v(">>> [$correlationId] ${request.method} ${request.uri}")
        request.headers.forEach { (k, v) -> Logger.v("  [$correlationId] $k: $v") }
        request.body?.let { Logger.v("  [$correlationId] Body: $it") }
    }

    private fun logResponse(correlationId: String, response: HttpResponse) {
        Logger.v("<<< [$correlationId] ${response.httpStatus}")
        response.headers.forEach { (k, vs) -> Logger.v("  [$correlationId] $k: ${vs.joinToString()}") }
        Logger.v("  [$correlationId] Body: ${response.getData()}")
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
    }
}
