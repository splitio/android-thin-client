package io.split.client.thin.internal

internal interface InternalDestroyable {
    suspend fun tearDownInternal()
}
