package io.split.client.thin.internal.streaming

interface StreamingManager {
    suspend fun start()
    suspend fun stop()
    fun pause()
    fun resume()
    suspend fun stopAll()
}
