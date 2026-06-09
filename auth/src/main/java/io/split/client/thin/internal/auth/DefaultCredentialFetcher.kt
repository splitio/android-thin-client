package io.split.client.thin.internal.auth

import io.split.client.thin.http.HttpRequestDescriptor
import io.split.client.thin.http.RequestCategory
import io.split.client.thin.http.RetryableHttpClient
import io.split.client.thin.http.contracts.HttpMethod
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal class DefaultCredentialFetcher(
    private val retryableHttpClient: RetryableHttpClient,
    private val sdkKey: String,
    private val serviceUrl: String,
    private val tokenDeserializer: TokenDeserializer = JsonTokenDeserializer(),
    private val onJwtFetchStarted: (target: String) -> Unit = {},
    private val onJwtFetchSucceeded: (credential: JwtCredential, target: String) -> Unit = { _, _ -> },
    private val onJwtFetchFailedNonRetryable: (target: String, error: Exception) -> Unit = { _, _ -> },
    private val onUnauthorized: (target: String) -> Unit = {},
) : CredentialFetcher {

    override suspend fun fetchCredential(targets: Set<String>): JwtCredential {
        val sep = if (serviceUrl.contains('?')) "&" else "?"
        val usersParams = targets.sorted().joinToString("&") { t ->
            "key=${URLEncoder.encode(t, StandardCharsets.UTF_8.name())}"
        }
        val request = HttpRequestDescriptor(
            uri = URI.create("$serviceUrl${sep}${usersParams}"),
            method = HttpMethod.GET,
            headers = mapOf("Authorization" to "Bearer $sdkKey"),
        )
        val targetKey = targets.sorted().joinToString(",")
        onJwtFetchStarted(targetKey)
        try {
            val response = retryableHttpClient.execute(request, RequestCategory.AUTH)
            if (response.httpStatus == HTTP_UNAUTHORIZED) {
                onUnauthorized(targetKey)
                throw IllegalStateException("Auth request failed with 401 Unauthorized")
            }
            val data = response.getData() ?: throw IllegalStateException("Auth response body is null")
            val credential = tokenDeserializer.deserialize(data)
            onJwtFetchSucceeded(credential, targetKey)
            return credential
        } catch (e: Exception) {
            onJwtFetchFailedNonRetryable(targetKey, e)
            throw e
        }
    }

    private companion object {
        private const val HTTP_UNAUTHORIZED = 401
    }
}
