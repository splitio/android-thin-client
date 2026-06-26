package io.split.client.thin.internal.auth

internal interface CredentialStorage {
    suspend fun getCredential(): JwtCredential?
    suspend fun saveCredential(credential: JwtCredential)
    suspend fun removeCredential()
}
