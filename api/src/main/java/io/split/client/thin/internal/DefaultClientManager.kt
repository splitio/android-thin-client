package io.split.client.thin.internal

import io.split.client.thin.Key
import io.split.client.thin.SplitClient
import io.split.client.thin.Target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class DefaultClientManager(
    private val scope: CoroutineScope,
    private val clientFactory: (Target) -> SplitClient,
) : ClientManager {

    private val clients = HashMap<Key, SplitClient>()
    private val lastTargets = HashMap<Key, Target>()
    private val lock = Any()

    override fun getOrCreate(target: Target): SplitClient {
        val (client, targetChanged) = synchronized(lock) {
            val existing = clients[target.key]
            if (existing != null) {
                val changed = lastTargets[target.key] != target
                if (changed) lastTargets[target.key] = target
                Pair(existing, changed)
            } else {
                val newClient = clientFactory(target)
                clients[target.key] = newClient
                lastTargets[target.key] = target
                Pair(newClient, false)
            }
        }
        if (targetChanged) scope.launch {
            // Skip the call if stale.
            val stillCurrent = synchronized(lock) {
                lastTargets[target.key] == target && clients[target.key] === client
            }
            if (stillCurrent) {
                runCatching {
                    client.setTarget(target)
                }
            }
        }

        return client
    }

    override suspend fun destroy(key: Key) {
        val client = synchronized(lock) {
            lastTargets.remove(key)
            clients.remove(key)
        } ?: return
        client.destroy()
    }

    override suspend fun destroyAll() {
        val all = synchronized(lock) {
            val copy = clients.values.toList()
            clients.clear()
            lastTargets.clear()
            copy
        }
        all.forEach { runCatching { it.destroy() } }
    }
}
