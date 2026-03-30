package io.split.client.thin.internal.auth

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

internal class DefaultAuthProvider<T : AuthParamsProvider>(
    private val credentialFetcher: CredentialFetcher<T>,
    private val credentialStorage: CredentialStorage,
    private val compositeKeyBuilder: (Set<T>) -> T,
    private val onJwtRequestStarted: (target: T) -> Unit = {},
    private val onJwtReturnedFromStorage: (credential: JwtCredential, target: T) -> Unit = { _, _ -> },
    private val onJwtExpiredOrInvalid: (target: T) -> Unit = {},
    private val onJwtStored: (credential: JwtCredential, target: T) -> Unit = { _, _ -> },
) : AuthProvider<T> {

    private val mutex = Mutex()
    // Completion callbacks may run on different threads, so this map must be thread-safe.
    private val inFlight = ConcurrentHashMap<T, Deferred<JwtCredential>>()

    override suspend fun credential(targets: Set<T>): JwtCredential {
        val compositeKey = compositeKeyBuilder(targets.sortedBy { it.getUsers() }.toSet())
        onJwtRequestStarted(compositeKey)
        val stored = credentialStorage.getCredential()
        if (stored != null && !stored.isExpired()) {
            onJwtReturnedFromStorage(stored, compositeKey)
            return stored
        } else if (stored != null) {
            onJwtExpiredOrInvalid(compositeKey)
        }

        return fetchDeduplicated(compositeKey)
    }

    override suspend fun invalidateAll() {
        mutex.withLock {
            inFlight.clear()
        }
        credentialStorage.removeCredential()
    }

    private suspend fun fetchDeduplicated(target: T): JwtCredential = coroutineScope {
        val deferred = mutex.withLock {
            inFlight.getOrPut(target) {
                async {
                    val credential = credentialFetcher.fetchCredential(target)
                    credentialStorage.saveCredential(credential)
                    onJwtStored(credential, target)
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
