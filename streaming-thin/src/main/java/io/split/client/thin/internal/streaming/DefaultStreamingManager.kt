package io.split.client.thin.internal.streaming

import io.split.android.client.backoff.BackoffCounter
import io.split.android.client.service.sseclient.sseclient.EventSourceClient
import io.split.client.thin.internal.observer.CompositeObserver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

internal class DefaultStreamingManager(
    private val streamingUrl: String,
    private val tokenProvider: suspend () -> StreamingToken,
    private val eventSourceClientProvider: () -> EventSourceClient,
    private val backoffCounterFactory: () -> BackoffCounter,
    private val scope: CoroutineScope,
    private val onEvaluationFetchNotification: suspend (EvaluationUpdateNotification?) -> Unit,
    private val onPushDisabled: suspend () -> Unit = {},
    private val onPushEnabled: suspend () -> Unit = {},
    private val invalidateToken: suspend () -> Unit = {},
    private val connectionDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val observer: CompositeObserver,
    private val evalChangeNumberHolder: AtomicLong = AtomicLong(Long.MIN_VALUE),
) : StreamingManager {

    private val mutex = Mutex()
    private var connectionManager: StreamingConnectionManager? = null
    private var startRequested = false
    private var paused = false

    override suspend fun start() {
        val manager = mutex.withLock {
            startRequested = true
            if (paused) return
            connectionManager ?: createConnectionManager().also { connectionManager = it }
        }
        manager.start()
    }

    override suspend fun stop() {
        mutex.withLock {
            connectionManager?.stop()
        }
    }

    override fun pause() {
        scope.launch {
            val manager = mutex.withLock {
                paused = true
                connectionManager
            }
            manager?.pause()
        }
    }

    override fun resume() {
        scope.launch {
            val manager = mutex.withLock {
                paused = false
                if (!startRequested) return@launch
                connectionManager ?: createConnectionManager().also { connectionManager = it }
            }
            manager.start()
        }
    }

    override suspend fun stopAll() {
        val manager = mutex.withLock {
            startRequested = false
            connectionManager.also { connectionManager = null }
        }
        manager?.stop()
    }

    private fun createConnectionManager(): StreamingConnectionManager {
        return StreamingConnectionManager(
            streamingUrl = streamingUrl,
            tokenProvider = tokenProvider,
            eventSourceClientProvider = eventSourceClientProvider,
            backoffCounter = backoffCounterFactory(),
            scope = scope,
            connectionDispatcher = connectionDispatcher,
            onEvaluationFetchNotification = onEvaluationFetchNotification,
            onPushDisabled = onPushDisabled,
            onPushEnabled = onPushEnabled,
            invalidateToken = invalidateToken,
            observer = observer,
            evalChangeNumberHolder = evalChangeNumberHolder,
        )
    }
}
