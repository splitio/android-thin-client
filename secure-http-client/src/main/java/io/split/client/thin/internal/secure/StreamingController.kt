package io.split.client.thin.internal.secure

interface StreamingController {
    suspend fun start()
    suspend fun stop()
    suspend fun pause()
    suspend fun resume()
}
