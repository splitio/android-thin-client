package io.split.client.thin.internal.auth

internal interface CredentialStorage<T : Any> {
    suspend fun getCredential(target: T): JwtCredential?
    suspend fun saveCredential(credential: JwtCredential, target: T)
    suspend fun removeCredential(target: T)
}
