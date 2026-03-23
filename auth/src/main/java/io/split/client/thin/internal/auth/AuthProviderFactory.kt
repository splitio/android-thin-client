package io.split.client.thin.internal.auth

import io.split.client.thin.http.RetryableHttpClient

fun <T : AuthParamsProvider> createAuthProvider(
    retryableHttpClient: RetryableHttpClient,
    sdkKey: String,
    authUrl: String,
): AuthProvider<T> {
    val storage = InMemoryCredentialStorage<T>()
    val fetcher = DefaultCredentialFetcher<T>(
        retryableHttpClient = retryableHttpClient,
        sdkKey = sdkKey,
        serviceUrl = authUrl,
    )
    return DefaultAuthProvider(fetcher, storage)
}
