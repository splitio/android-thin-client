package io.split.client.thin.internal.auth

import io.split.android.client.network.HttpMethod
import io.split.client.thin.http.HttpRequestDescriptor
import io.split.client.thin.http.RequestCategory
import io.split.client.thin.http.RetryableHttpClient
import java.net.URI

internal class DefaultCredentialFetcher(
    private val retryableHttpClient: RetryableHttpClient,
    private val sdkKey: String,
    private val serviceUrl: String,
    private val tokenDeserializer: TokenDeserializer = JsonTokenDeserializer(),
    private val onJwtFetchStarted: (target: String) -> Unit = {},
    private val onJwtFetchSucceeded: (credential: JwtCredential, target: String) -> Unit = { _, _ -> },
    private val onJwtFetchFailedNonRetryable: (target: String, error: Exception) -> Unit = { _, _ -> },
) : CredentialFetcher {

    override suspend fun fetchCredential(target: String): JwtCredential {
        val request = HttpRequestDescriptor(
            uri = URI.create("$serviceUrl/?users=$target"),
            method = HttpMethod.GET,
            headers = mapOf("Authorization" to "Bearer $sdkKey"),
        )
        onJwtFetchStarted(target)
        try {
            val response = retryableHttpClient.execute(request, RequestCategory.AUTH)
            val credential = tokenDeserializer.deserialize(response.data)
            onJwtFetchSucceeded(credential, target)
            return credential
        } catch (e: Exception) {
            onJwtFetchFailedNonRetryable(target, e)
            throw e
        }
    }
}
