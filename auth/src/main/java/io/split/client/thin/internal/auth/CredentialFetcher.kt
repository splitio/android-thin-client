package io.split.client.thin.internal.auth

internal fun interface CredentialFetcher {
    suspend fun fetchCredential(targets: Set<String>): JwtCredential
}
