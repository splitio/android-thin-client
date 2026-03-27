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
        val compositeKey = compositeKeyBuilder(targets)
        onJwtRequestStarted(compositeKey)
        val stored = credentialStorage.getCredential(compositeKey)
        if (stored != null && !stored.isExpired()) {
            onJwtReturnedFromStorage(stored, compositeKey)
            return stored
        } else if (stored != null) {
            onJwtExpiredOrInvalid(compositeKey)
        }

        val credential = fetchDeduplicated(compositeKey)
        targets.forEach { credentialStorage.saveCredential(credential, it) }
        return credential
    }

    override suspend fun invalidateAll(targets: Set<T>) {
        val compositeKey = compositeKeyBuilder(targets)
        (targets + compositeKey).forEach { invalidate(it) }
    }

    private suspend fun invalidate(target: T) {
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
