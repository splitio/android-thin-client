package io.split.client.thin.internal.auth

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

internal class DefaultAuthProvider<T : AuthParamsProvider>(
    private val credentialFetcher: CredentialFetcher<T>,
    private val credentialStorage: CredentialStorage<T>,
) : AuthProvider<T> {

    private val mutex = Mutex()
    // Completion callbacks may run on different threads, so this map must be thread-safe.
    private val inFlight = ConcurrentHashMap<T, Deferred<JwtCredential>>()

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
                    credential
                }.also { newDeferred ->
                    // Always clear the entry when this deferred completes (success, failure, or cancellation).
                    newDeferred.invokeOnCompletion {
                        // Remove only if the same deferred is still registered for this target.
                        inFlight.remove(target, newDeferred)
                    }
                }
            }
        }
        deferred.await()
    }
}
