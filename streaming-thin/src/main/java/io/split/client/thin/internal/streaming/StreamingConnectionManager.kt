package io.split.client.thin.internal.streaming

import io.split.android.client.backoff.BackoffCounter
import io.split.android.client.service.sseclient.sseclient.EventSourceClient
import io.split.android.client.utils.logger.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URI

data class StreamingToken(
    val token: String,
    val connDelaySeconds: Long = 0,
    val pushEnabled: Boolean = true,
)

class StreamingConnectionManager(
    private val streamingUrl: String,
    private val tokenProvider: suspend () -> StreamingToken,
    private val channelExtractor: (String) -> List<String> = SseJwtParser()::parse,
    private val eventSourceClientProvider: () -> EventSourceClient,
    private val backoffCounter: BackoffCounter,
    private val scope: CoroutineScope,
    private val onOccupancyZero: suspend () -> Unit,
    private val onEvaluationFetchNotification: suspend (EvaluationUpdateNotification?) -> Unit,
    private val onPushDisabled: suspend () -> Unit = {},
    private val connectionDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val stateMutex = Mutex()
    private var state: ConnectionState = ConnectionState.Stopped
    private var connectionJob: Job? = null
    private var currentEventSourceClient: EventSourceClient? = null
    private val notificationParser = ThinNotificationParser()

    private sealed class ConnectionState {
        object Stopped : ConnectionState()
        object Started : ConnectionState()
        object Paused : ConnectionState()
    }

    suspend fun start() {
        stateMutex.withLock {
            if (state is ConnectionState.Started) {
                // Already started, idempotent
                return
            }
            state = ConnectionState.Started
        }
        connect()
    }

    suspend fun stop() {
        stateMutex.withLock {
            state = ConnectionState.Stopped
            disconnectLocked()
        }
    }

    suspend fun pause() {
        stateMutex.withLock {
            if (state is ConnectionState.Started) {
                state = ConnectionState.Paused
                disconnectLocked()
            }
        }
    }

    suspend fun resume() {
        stateMutex.withLock {
            if (state is ConnectionState.Paused) {
                state = ConnectionState.Started
            } else if (state is ConnectionState.Stopped) {
                // Resume when not started is a no-op
                return
            }
        }
        connect()
    }

    private fun disconnectLocked() {
        connectionJob?.cancel()
        connectionJob = null
        currentEventSourceClient?.disconnect()
        currentEventSourceClient = null
    }

    private fun connect() {
        connectionJob = scope.launch {
            try {
                val streamingToken = tokenProvider()
                if (!streamingToken.pushEnabled) {
                    stop()
                    onPushDisabled()
                    return@launch
                }
                if (streamingToken.connDelaySeconds > 0) {
                    delay(streamingToken.connDelaySeconds * 1_000L)
                }
                val token = streamingToken.token
                val channels = channelExtractor(token)
                val uri = URI("$streamingUrl?v=1.1&channel=${channels.joinToString(",")}&accessToken=$token")

                // Create new EventSourceClient instance
                val client = eventSourceClientProvider()
                currentEventSourceClient = client

                // EventSourceClient.connect() is blocking, so run in IO dispatcher
                withContext(connectionDispatcher) {
                    client.connect(uri, createEventHandler())
                }
            } catch (e: Exception) {
                Logger.e("Streaming connection failed: ${e.message}")
                handleConnectionError(retryable = true)
            }
        }
    }

    private fun createEventHandler() = object : EventSourceClient.EventHandler {
        override fun onOpen() {
            backoffCounter.resetCounter()
            scope.launch { onEvaluationFetchNotification(null) }
        }

        override fun onMessage(event: Map<String, String>) {
            handleMessage(event)
        }

        override fun onError(retryable: Boolean) {
            handleConnectionError(retryable)
        }
    }

    private fun handleConnectionError(retryable: Boolean) {
        if (!retryable) {
            return
        }

        scope.launch {
            val shouldReconnect = stateMutex.withLock {
                state is ConnectionState.Started
            }

            if (!shouldReconnect) {
                return@launch
            }

            val delayMs = backoffCounter.getNextRetryTime()
            delay(delayMs)

            val stillStarted = stateMutex.withLock {
                state is ConnectionState.Started
            }

            if (stillStarted) {
                connect()
            }
        }
    }

    private fun handleMessage(event: Map<String, String>) {
        val jsonData = event["data"] ?: return
        val raw = notificationParser.parseRaw(jsonData) ?: return
        val notification = notificationParser.parse(raw) ?: return

        scope.launch {
            when (notification) {
                is EvaluationUpdateNotification -> {
                    onEvaluationFetchNotification.invoke(notification)
                }
                is ThinControlNotification -> {
                    when (notification.controlType) {
                        ThinControlNotification.ControlType.STREAMING_RESUMED -> resume()
                        ThinControlNotification.ControlType.STREAMING_PAUSED -> pause()
                        ThinControlNotification.ControlType.STREAMING_DISABLED -> stop()
                        ThinControlNotification.ControlType.STREAMING_RESET -> {
                            stop()
                            start()
                        }
                    }
                }
                is ThinOccupancyNotification -> {
                    if (notification.publishers == 0) {
                        onOccupancyZero()
                        stop()
                    }
                }
                is ThinStreamingError -> {
                    Logger.e("Streaming error: ${notification.message} (code: ${notification.code})")
                }
            }
        }
    }
}
