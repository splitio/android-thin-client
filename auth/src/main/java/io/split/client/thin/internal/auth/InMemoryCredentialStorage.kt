package io.split.client.thin.internal.auth

import java.util.concurrent.ConcurrentHashMap

internal class InMemoryCredentialStorage<T : Any>(
    private val secureStorage: SecureStorage<T> = NoOpSecureStorage(),
) : CredentialStorage<T> {

    private val cache = ConcurrentHashMap<T, JwtCredential>()

    override suspend fun getCredential(target: T): JwtCredential? {
        return cache[target] ?: secureStorage.getCredential(target)?.also {
            cache[target] = it
        }
    }

    override suspend fun saveCredential(credential: JwtCredential, target: T) {
        cache[target] = credential
        secureStorage.saveCredential(credential, target)
    }

    override suspend fun removeCredential(target: T) {
        cache.remove(target)
        secureStorage.removeCredential(target)
    }
}
