package io.split.client.thin.internal

import io.split.client.thin.Key
import io.split.client.thin.SplitClient
import io.split.client.thin.Target

internal interface ClientManager {

    /**
     * Returns an existing client for the target's key, or creates a new one.
     * If the key already exists but the target differs, [SplitClient.setTarget] is fired
     * asynchronously (fire-and-forget).
     */
    fun getOrCreate(target: Target): SplitClient

    /**
     * Destroys the client registered under [key], flushing pending data first.
     * No-op if no client is registered for that key.
     */
    suspend fun destroy(key: Key)

    /**
     * Destroys all registered clients and clears the internal registry.
     */
    suspend fun destroyAll()

    /**
     * Starts polling for all existing clients and ensures future clients also start polling.
     * Used when switching from streaming to polling (e.g., when server returns pushEnabled=false).
     */
    fun startAllPolling()
}
