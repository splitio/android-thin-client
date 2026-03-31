package io.split.client.thin.internal.auth

internal class InMemoryCredentialStorage(
    private val secureStorage: SecureStorage = NoOpSecureStorage(),
) : CredentialStorage {

    @Volatile
    private var cached: JwtCredential? = null

    override suspend fun getCredential(): JwtCredential? {
        return cached ?: secureStorage.getCredential()?.also {
            cached = it
        }
    }

    override suspend fun saveCredential(credential: JwtCredential) {
        cached = credential
        secureStorage.saveCredential(credential)
    }

    override suspend fun removeCredential() {
        cached = null
        secureStorage.removeCredential()
    }
}
