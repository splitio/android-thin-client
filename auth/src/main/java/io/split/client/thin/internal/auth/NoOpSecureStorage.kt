package io.split.client.thin.internal.auth

internal class NoOpSecureStorage<T : Any> : SecureStorage<T> {

    override suspend fun getCredential(target: T): JwtCredential? = null

    override suspend fun saveCredential(credential: JwtCredential, target: T) = Unit

    override suspend fun removeCredential(target: T) = Unit
}
