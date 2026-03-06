package io.split.client.thin.internal

import io.split.client.thin.Key
import io.split.client.thin.SdkKey
import io.split.client.thin.SplitClient
import io.split.client.thin.SplitClientConfig
import io.split.client.thin.SplitFactory
import io.split.client.thin.SplitManager
import io.split.client.thin.SplitVoidCallback
import io.split.client.thin.Target

internal class DefaultSplitFactory(
    private val sdkKey: SdkKey,
    private val defaultTarget: Target,
    private val config: SplitClientConfig?,
    private val asyncBridge: AsyncBridgeLike,
    private val clientManager: ClientManager = object: ClientManager {
        override fun getOrCreate(target: Target): SplitClient {
            TODO("Not yet implemented")
        }

        override suspend fun destroy(key: Key) {
            TODO("Not yet implemented")
        }

        override suspend fun destroyAll() {
            TODO("Not yet implemented")
        }
    },
) : SplitFactory {

    override fun getClient(target: Target?): SplitClient {
        return clientManager.getOrCreate(target ?: defaultTarget)
    }

    override fun getManager(): SplitManager {
        TODO("Not yet implemented")
    }

    override suspend fun destroy() {
        clientManager.destroyAll()
        asyncBridge.close()
    }

    @Deprecated("Use suspend destroy()", level = DeprecationLevel.ERROR)
    override fun destroyAsync(callback: SplitVoidCallback) =
        asyncBridge.executeAsync(callback) { destroy() }
}
