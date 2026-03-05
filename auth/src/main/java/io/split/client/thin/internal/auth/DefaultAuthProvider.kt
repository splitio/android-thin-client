package io.split.client.thin.internal.auth

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class DefaultAuthProvider<T : Any>(
    private val credentialFetcher: CredentialFetcher<T>,
    private val credentialStorage: CredentialStorage<T>,
) : AuthProvider<T> {

    private val mutex = Mutex()
    private val inFlight = mutableMapOf<T, Deferred<JwtCredential>>()

    override suspend fun credential(target: T): JwtCredential {
        val stored = credentialStorage.getCredential(target)
        if (stored != null && !stored.isExpired()) {
            return stored
        }

        return fetchDeduplicated(target)
    }

    override suspend fun invalidate(target: T) {
        mutex.withLock {
            inFlight.remove(target)
        }
        credentialStorage.removeCredential(target)
    }

    private suspend fun fetchDeduplicated(target: T): JwtCredential = coroutineScope {
        val deferred = mutex.withLock {
            inFlight.getOrPut(target) {
                async {
                    val credential = credentialFetcher.fetchCredential(target)
                    credentialStorage.saveCredential(credential, target)
                    mutex.withLock { inFlight.remove(target) }
                    credential
                }
            }
        }
        deferred.await()
    }
}
