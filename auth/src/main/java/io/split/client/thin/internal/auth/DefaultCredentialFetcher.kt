package io.split.client.thin.internal.auth

import io.split.android.client.network.HttpMethod
import io.split.client.thin.http.HttpRequestDescriptor
import io.split.client.thin.http.RequestCategory
import io.split.client.thin.http.RetryableHttpClient
import java.net.URI

internal class DefaultCredentialFetcher<T : AuthParamsProvider>(
    private val retryableHttpClient: RetryableHttpClient,
    private val sdkKey: String,
    private val serviceUrl: String,
    private val tokenDeserializer: TokenDeserializer = JsonTokenDeserializer(),
) : CredentialFetcher<T> {

    override suspend fun fetchCredential(target: T): JwtCredential {
        val request = HttpRequestDescriptor(
            uri = URI.create("$serviceUrl/v1/auth?users=${target.getUsers()}"),
            method = HttpMethod.GET,
            headers = mapOf("Authorization" to "Bearer $sdkKey"),
        )
        val response = retryableHttpClient.execute(request, RequestCategory.AUTH)
        return tokenDeserializer.deserialize(response.data)
    }
}
