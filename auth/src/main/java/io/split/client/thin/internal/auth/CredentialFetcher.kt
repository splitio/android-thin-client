package io.split.client.thin.internal.auth

internal fun interface CredentialFetcher {
    suspend fun fetchCredential(target: String): JwtCredential
}
