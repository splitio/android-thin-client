package io.split.client.thin.internal.auth

internal interface SecureStorage {
    suspend fun getCredential(): JwtCredential?
    suspend fun saveCredential(credential: JwtCredential)
    suspend fun removeCredential()
}
