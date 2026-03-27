package io.split.client.thin.internal.streaming

import io.split.android.client.backoff.BackoffCounter
import io.split.android.client.service.sseclient.sseclient.EventSourceClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DefaultStreamingManager(
    private val streamingUrl: String,
    private val tokenProvider: suspend () -> String,
    private val eventSourceClientProvider: () -> EventSourceClient,
    private val backoffCounterFactory: () -> BackoffCounter,
    private val scope: CoroutineScope,
    private val onOccupancyZero: suspend () -> Unit,
    private val onEvaluationFetchNotification: suspend () -> Unit,
    private val connectionDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StreamingManager {

    private val mutex = Mutex()
    private var connectionManager: StreamingConnectionManager? = null

    override suspend fun start() {
        mutex.withLock {
            if (connectionManager == null) {
                connectionManager = createConnectionManager()
            }
        }
        connectionManager?.start()
    }

    override suspend fun stop() {
        mutex.withLock {
            connectionManager?.stop()
        }
    }

    override fun pause() {
        scope.launch {
            mutex.withLock {
                connectionManager?.pause()
            }
        }
    }

    override fun resume() {
        scope.launch {
            mutex.withLock {
                connectionManager?.resume()
            }
        }
    }

    override suspend fun stopAll() {
        mutex.withLock {
            connectionManager?.stop()
            connectionManager = null
        }
    }

    private fun createConnectionManager(): StreamingConnectionManager {
        return StreamingConnectionManager(
            streamingUrl = streamingUrl,
            tokenProvider = tokenProvider,
            eventSourceClientProvider = eventSourceClientProvider,
            backoffCounter = backoffCounterFactory(),
            scope = scope,
            connectionDispatcher = connectionDispatcher,
            onOccupancyZero = onOccupancyZero,
            onEvaluationFetchNotification = onEvaluationFetchNotification,
        )
    }
}
