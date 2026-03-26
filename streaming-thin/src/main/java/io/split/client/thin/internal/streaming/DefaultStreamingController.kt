package io.split.client.thin.internal.streaming

import io.split.client.thin.internal.secure.StreamingController

class DefaultStreamingController(
    private val connectionManager: StreamingConnectionManager
) : StreamingController {
    override suspend fun start() = connectionManager.start()
    override suspend fun stop() = connectionManager.stop()
    override suspend fun pause() = connectionManager.pause()
    override suspend fun resume() = connectionManager.resume()
}
