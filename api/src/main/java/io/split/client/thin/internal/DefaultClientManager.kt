package io.split.client.thin.internal

import io.split.client.thin.Key
import io.split.client.thin.SplitClient
import io.split.client.thin.Target
import io.split.client.thin.internal.evaluation.toEvaluationKey
import io.split.client.thin.internal.evaluation.toEvaluationTarget
import io.split.client.thin.internal.auth.AuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class DefaultClientManager(
    private val scope: CoroutineScope,
    private val clientFactory: (Target) -> SplitClient,
    private val authProvider: AuthProvider? = null,
    private val onTargetsEmpty: (suspend () -> Unit)? = null,
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
                val isNew = authProvider?.addTarget(target.toEvaluationKey().toEvaluationTarget().matchingKey) ?: false
                if (isNew) scope.launch { authProvider?.invalidateAll() }
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
        val (client, target) = synchronized(lock) {
            val t = lastTargets.remove(key)
            val c = clients.remove(key)
            Pair(c, t)
        }
        client ?: return
        client.destroy()
        if (authProvider != null && target != null) {
            val isEmpty = authProvider.removeTarget(target.toEvaluationKey().toEvaluationTarget().matchingKey)
            if (isEmpty) {
                onTargetsEmpty?.invoke()
            }
        }
    }

    override suspend fun destroyAll() {
        val (all, targets) = synchronized(lock) {
            val clients = clients.values.toList()
            val targets = lastTargets.values.toList()
            this.clients.clear()
            lastTargets.clear()
            Pair(clients, targets)
        }
        all.forEach { runCatching { it.destroy() } }
        if (authProvider != null) {
            for (target in targets) {
                authProvider.removeTarget(target.toEvaluationKey().toEvaluationTarget().matchingKey)
            }
        }
    }
}
