package io.split.client.thin.internal.auth

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

internal class DefaultAuthProvider(
    private val credentialFetcher: CredentialFetcher,
    private val credentialStorage: CredentialStorage,
    private val compositeKeyBuilder: (Set<String>) -> String,
    private val defaultTarget: String? = null,
    private val onJwtRequestStarted: (target: String) -> Unit = {},
    private val onJwtReturnedFromStorage: (credential: JwtCredential, target: String) -> Unit = { _, _ -> },
    private val onJwtExpiredOrInvalid: (target: String) -> Unit = {},
    private val onJwtStored: (credential: JwtCredential, target: String) -> Unit = { _, _ -> },
) : AuthProvider {

    private val mutex = Mutex()
    // Completion callbacks may run on different threads, so this map must be thread-safe.
    private val inFlight = ConcurrentHashMap<String, Deferred<JwtCredential>>()

    // Ref-counted map: matchingKey -> count of live clients using that key.
    // Multiple clients may share a matchingKey with different bucketingKeys;
    // removing a target drops it only when its ref-count reaches zero.
    private val activeTargetRefs = if (defaultTarget != null) mutableMapOf(defaultTarget to 1) else mutableMapOf<String, Int>()
    private val activeTargetsLock = Any()

    override fun addTarget(target: String): Boolean {
        return synchronized(activeTargetsLock) {
            val prev = activeTargetRefs[target] ?: 0
            activeTargetRefs[target] = prev + 1
            prev == 0 // returns true only when the target is newly added
        }
    }

    override fun removeTarget(target: String): Boolean {
        return synchronized(activeTargetsLock) {
            val prev = activeTargetRefs[target] ?: return@synchronized false
            if (prev <= 1) {
                activeTargetRefs.remove(target)
            } else {
                activeTargetRefs[target] = prev - 1
            }
            activeTargetRefs.isEmpty()
        }
    }

    override suspend fun credential(): JwtCredential {
        val effective = synchronized(activeTargetsLock) {
            activeTargetRefs.keys.toSet().ifEmpty { defaultTarget?.let { setOf(it) } ?: emptySet() }
        }
        return credential(effective)
    }

    override suspend fun credential(targets: Set<String>): JwtCredential {
        val compositeKey = compositeKeyBuilder(targets.sorted().toSet())
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
            val deferreds = inFlight.values.toList()
            inFlight.clear()
            deferreds.forEach { it.cancel() }
        }
        credentialStorage.removeCredential()
    }

    private suspend fun fetchDeduplicated(target: String): JwtCredential = coroutineScope {
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
