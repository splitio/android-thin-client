package io.split.client.thin.internal.auth

internal class NoOpSecureStorage : SecureStorage {

    override suspend fun getCredential(): JwtCredential? = null

    override suspend fun saveCredential(credential: JwtCredential) = Unit

    override suspend fun removeCredential() = Unit
}
