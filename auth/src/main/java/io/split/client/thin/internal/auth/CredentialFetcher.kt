package io.split.client.thin.internal.auth

internal fun interface CredentialFetcher<T : Any> {
    suspend fun fetchCredential(target: T): JwtCredential
}
