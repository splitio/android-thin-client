package io.split.client.thin.internal

import io.split.client.thin.SdkKey
import io.split.client.thin.SplitClient
import io.split.client.thin.SplitClientConfig
import io.split.client.thin.SplitFactory
import io.split.client.thin.SplitManager
import io.split.client.thin.SplitVoidCallback
import io.split.client.thin.Target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

internal class DefaultSplitFactory(
    private val sdkKey: SdkKey,
    private val defaultTarget: Target,
    private val config: SplitClientConfig?,
    private val asyncBridge: AsyncBridgeLike,
    private val clientFactory: (Target) -> SplitClient,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val clientManager: ClientManager = DefaultClientManager(clientFactory, scope),
) : SplitFactory {

    override fun getClient(target: Target?): SplitClient {
        return clientManager.getOrCreate(target ?: defaultTarget)
    }

    override fun getManager(): SplitManager {
        TODO("Not yet implemented")
    }

    override suspend fun destroy() {
        clientManager.destroyAll()
        scope.cancel()
        asyncBridge.close()
    }

    @Deprecated("Use suspend destroy()", level = DeprecationLevel.ERROR)
    override fun destroyAsync(callback: SplitVoidCallback) =
        asyncBridge.executeAsync(callback) { destroy() }
}
