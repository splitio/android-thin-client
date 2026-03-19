package io.split.client.thin.internal.auth

internal fun interface CredentialFetcher<T : AuthParamsProvider> {
    suspend fun fetchCredential(target: T): JwtCredential
}
