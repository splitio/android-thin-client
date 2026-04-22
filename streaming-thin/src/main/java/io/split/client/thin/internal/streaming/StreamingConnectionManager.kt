package io.split.client.thin.internal.streaming

import io.split.android.client.backoff.BackoffCounter
import io.split.android.client.service.sseclient.sseclient.EventSourceClient
import io.split.client.thin.internal.observer.CompositeObserver
import io.split.client.thin.internal.observer.ObservableEvent
import io.split.client.thin.internal.observer.ObservableEventType
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
import kotlin.coroutines.cancellation.CancellationException

data class StreamingToken(
    val token: String,
    val connDelaySeconds: Long = 0,
    val pushEnabled: Boolean = true,
)

internal class StreamingConnectionManager(
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
    private val observer: CompositeObserver,
) {
    private val stateMutex = Mutex()
    private var state: ConnectionState = ConnectionState.Stopped
    private var connectionJob: Job? = null
    private var currentEventSourceClient: EventSourceClient? = null
    private val notificationParser = ThinNotificationParser()
    private val occupancyByChannel = mutableMapOf<String, Int>()
    private var hadPublishers = false

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
        observer.notifyEvent(ObservableEvent(ObservableEventType.STREAMING_DISCONNECTED))
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
            } else {
                // Already started or stopped — nothing to do
                return
            }
        }
        connect()
    }

    private fun disconnectLocked() {
        connectionJob?.cancel()
        connectionJob = null
        val client = currentEventSourceClient
        currentEventSourceClient = null
        // Disconnect asynchronously: BufferedReader.close() blocks while readLine()
        // holds its lock on the IO thread. Running disconnect in background prevents
        // pause() from holding the mutex for seconds, allowing resume() to proceed.
        if (client != null) {
            scope.launch(connectionDispatcher) { client.disconnect() }
        }
    }

    private fun connect() {
        observer.notifyEvent(ObservableEvent(ObservableEventType.STREAMING_CONNECT_STARTED))
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

                // Reset occupancy state for new connection
                occupancyByChannel.clear()
                hadPublishers = false

                // Create new EventSourceClient instance
                val client = eventSourceClientProvider()
                currentEventSourceClient = client

                // EventSourceClient.connect() is blocking, so run in IO dispatcher
                withContext(connectionDispatcher) {
                    client.connect(uri, createEventHandler())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleConnectionError(retryable = true)
            }
        }
    }

    private fun createEventHandler() = object : EventSourceClient.EventHandler {
        override fun onOpen() {
            observer.notifyEvent(ObservableEvent(ObservableEventType.STREAMING_CONNECTED))
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

            val delayMs = backoffCounter.getNextRetryTime() * 1_000L
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
                    observer.notifyEvent(ObservableEvent(
                        type = ObservableEventType.STREAMING_NOTIFICATION_RECEIVED,
                        properties = mapOf(
                            "notificationType" to "EVALUATIONS_UPDATE",
                            "rawData" to jsonData
                        )
                    ))
                    onEvaluationFetchNotification.invoke(notification)
                }
                is ThinControlNotification -> {
                    observer.notifyEvent(ObservableEvent(
                        type = ObservableEventType.STREAMING_NOTIFICATION_RECEIVED,
                        properties = mapOf(
                            "notificationType" to notification.controlType.name,
                            "rawData" to jsonData
                        )
                    ))
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
                    observer.notifyEvent(ObservableEvent(
                        type = ObservableEventType.STREAMING_NOTIFICATION_RECEIVED,
                        properties = mapOf(
                            "notificationType" to "OCCUPANCY",
                            "rawData" to jsonData
                        )
                    ))
                    notification.channelName?.let { occupancyByChannel[it] = notification.publishers }
                    val totalPublishers = occupancyByChannel.values.sum()
                    if (totalPublishers > 0) {
                        hadPublishers = true
                    } else if (hadPublishers) {
                        onOccupancyZero()
                        stop()
                    }
                }
                is ThinStreamingError -> {
                    observer.notifyEvent(ObservableEvent(
                        type = ObservableEventType.STREAMING_NOTIFICATION_RECEIVED,
                        properties = mapOf(
                            "notificationType" to "STREAMING_ERROR",
                            "errorCode" to notification.code.toString(),
                            "errorMessage" to (notification.message ?: ""),
                            "rawData" to jsonData
                        )
                    ))
                }
            }
        }
    }
}
