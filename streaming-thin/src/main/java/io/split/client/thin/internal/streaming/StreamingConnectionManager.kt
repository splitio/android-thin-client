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
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException

data class StreamingToken(
    val token: String,
    val connDelaySeconds: Long = 0,
    val pushEnabled: Boolean = true,
)

/**
 * Effect runtime for the streaming connection. It owns the *mechanism* — token fetch, socket I/O,
 * notification parsing, occupancy aggregation, eval-fetch debounce and reconnect timers — and
 * delegates every *decision* to the pure [StreamingPolicy]. External triggers, socket callbacks and
 * parsed notifications are translated into [PolicyEvent]s and fed through a single serialized
 * [dispatch]; the resulting [PolicyEffect]s are executed by [execute].
 */
internal class StreamingConnectionManager(
    private val streamingUrl: String,
    private val tokenProvider: suspend () -> StreamingToken,
    private val channelExtractor: (String) -> List<String> = SseJwtParser()::parse,
    private val eventSourceClientProvider: () -> EventSourceClient,
    private val backoffCounter: BackoffCounter,
    private val scope: CoroutineScope,
    private val onEvaluationFetchNotification: suspend (EvaluationUpdateNotification?) -> Unit,
    private val onPushDisabled: suspend () -> Unit = {},
    private val onPushEnabled: suspend () -> Unit = {},
    private val invalidateToken: suspend () -> Unit = {},
    private val connectionDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val observer: CompositeObserver,
    // Highest change number seen across eval notifications. Shared with the fetch handler so an
    // in-flight (parked) fetch can catch up to the latest change number without restarting its delay.
    private val evalChangeNumberHolder: AtomicLong = AtomicLong(Long.MIN_VALUE),
) {
    // Single serialization point: reduce runs under the lock (fast, pure); effects run outside it
    // (they may suspend, launch coroutines or post follow-up events).
    private val dispatchMutex = Mutex()
    private var policyState = PolicyState()

    // Lock-free snapshots of the two policy fields read on mechanism hot paths (the eval-drop gate
    // and the Stopped drops in onMessage/onOpen). Written only by [dispatch] after each reduce.
    @Volatile
    private var pushUp = true

    @Volatile
    private var connState: ConnState = ConnState.Stopped

    private var connectionJob: Job? = null
    private var notificationHandlerJob: Job? = null
    private var controlHandlerJob: Job? = null
    // Whether the in-flight eval fetch job was launched for an UNBOUNDED notification. Only unbounded
    // fetches can be safely coalesced (a bounded fetch carries a key bitmap that must not be lost).
    private var inFlightEvalUnbounded = false

    @Volatile
    private var currentEventSourceClient: EventSourceClient? = null
    private val notificationParser = ThinNotificationParser()
    private val occupancyTracker = OccupancyTracker()

    suspend fun start() {
        dispatch(PolicyEvent.Start)
    }

    suspend fun stop() {
        teardownConnectionJobs()
        dispatch(PolicyEvent.Stop)
    }

    suspend fun pause() {
        teardownConnectionJobs()
        dispatch(PolicyEvent.Pause)
    }

    suspend fun resume() {
        dispatch(PolicyEvent.Resume)
    }

    /**
     * Serialized entry point: under the lock run the pure [StreamingPolicy.reduce] to get the next
     * state + effects and refresh the volatile mirrors; release the lock; then execute the effects.
     */
    private suspend fun dispatch(event: PolicyEvent) {
        val effects = dispatchMutex.withLock {
            val (next, fx) = StreamingPolicy.reduce(policyState, event)
            policyState = next
            pushUp = next.pushUp
            connState = next.connState
            fx
        }
        execute(effects)
    }

    private suspend fun execute(effects: List<PolicyEffect>) {
        for (effect in effects) {
            when (effect) {
                PolicyEffect.OpenSocket -> runConnect()
                PolicyEffect.CloseCurrentSocket -> closeCurrentSocket()
                PolicyEffect.ScheduleReconnect -> scheduleReconnect()
                PolicyEffect.ResetBackoff -> backoffCounter.resetCounter()
                PolicyEffect.InvalidateToken -> invalidateToken()
                PolicyEffect.NotifyPushEnabled -> onPushEnabled()
                PolicyEffect.NotifyPushDisabled -> onPushDisabled()
                PolicyEffect.Fetch -> onEvaluationFetchNotification(null)
                PolicyEffect.EmitConnectStarted ->
                    observer.notifyEvent(ObservableEvent(ObservableEventType.STREAMING_CONNECT_STARTED))
                PolicyEffect.EmitConnected ->
                    observer.notifyEvent(ObservableEvent(ObservableEventType.STREAMING_CONNECTED))
                PolicyEffect.EmitDisconnected ->
                    observer.notifyEvent(ObservableEvent(ObservableEventType.STREAMING_DISCONNECTED))
                is PolicyEffect.EmitSyncModeChanged -> notifySyncModeChanged(effect.to, effect.reason)
            }
        }
    }

    private fun teardownConnectionJobs() {
        connectionJob?.cancel()
        connectionJob = null
        notificationHandlerJob?.cancel()
        notificationHandlerJob = null
        controlHandlerJob?.cancel()
        controlHandlerJob = null
    }

    private fun closeCurrentSocket() {
        val client = currentEventSourceClient
        currentEventSourceClient = null
        // Disconnect asynchronously: BufferedReader.close() blocks while readLine() holds its lock
        // on the IO thread. Running disconnect in the background keeps the dispatcher free.
        if (client != null) {
            scope.launch(connectionDispatcher) { client.disconnect() }
        }
    }

    private fun scheduleReconnect() {
        // Fire-and-forget: correctness is guarded by ReconnectTimerFired, which no-ops when the
        // reconnect was superseded (stop/pause cleared the dedup flag) or the lifecycle moved on.
        scope.launch {
            delay(backoffCounter.getNextRetryTime() * 1_000L)
            dispatch(PolicyEvent.ReconnectTimerFired)
        }
    }

    private fun runConnect() {
        connectionJob = scope.launch {
            try {
                val streamingToken = tokenProvider()
                if (!streamingToken.pushEnabled) {
                    dispatch(PolicyEvent.TokenPushDisabled)
                    return@launch
                }
                if (streamingToken.connDelaySeconds > 0) {
                    delay(streamingToken.connDelaySeconds * 1_000L)
                }
                val token = streamingToken.token
                val channels = channelExtractor(token)
                val uri = URI("$streamingUrl?v=1.1&channel=${channels.joinToString(",")}&accessToken=$token")

                // Reset occupancy state for new connection
                occupancyTracker.reset()

                // Create new EventSourceClient instance
                val client = eventSourceClientProvider()
                currentEventSourceClient = client

                // The connection may have been stopped/paused while the token was being fetched or
                // while the pre-connect delay elapsed. Re-check before opening the socket.
                if (connState != ConnState.Started) {
                    client.disconnect()
                    return@launch
                }

                // EventSourceClient.connect() is blocking, so run in IO dispatcher
                withContext(connectionDispatcher) {
                    client.connect(uri, createEventHandler(client))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dispatch(PolicyEvent.SocketError(retryable = true))
            }
        }
    }

    private fun createEventHandler(client: EventSourceClient) = object : EventSourceClient.EventHandler {
        override fun onOpen() {
            // The blocking connect cannot be interrupted, so a connection may open after
            // stop()/pause()/reconnect already ran. If this client is no longer the authoritative
            // one, tear it down and suppress its callbacks. Otherwise we'd emit STREAMING_CONNECTED
            // and trigger a fetch after the client was destroyed.
            if (currentEventSourceClient !== client || connState == ConnState.Stopped) {
                client.disconnect()
                return
            }
            scope.launch { dispatch(PolicyEvent.SocketOpened) }
        }

        override fun onMessage(event: Map<String, String>) {
            // A late event may arrive after stop() ran while the read loop was still blocked on the
            // throttled socket. Drop it so destroy/stop truly halts sync.
            if (connState == ConnState.Stopped) {
                return
            }
            handleMessage(event)
        }

        override fun onError(retryable: Boolean) {
            scope.launch { dispatch(PolicyEvent.SocketError(retryable)) }
        }
    }

    /**
     * Handles an `event: error` frame. Always emits an observability event. The token-error
     * classification feeds [PolicyEvent.ErrorFrame]; the policy decides the invalidate / close /
     * reconnect, while this per-frame observability event stays in the runtime.
     */
    private fun handleStreamingErrorFrame(jsonData: String?) {
        controlHandlerJob?.cancel()
        controlHandlerJob = scope.launch {
            val error = notificationParser.parseErrorFrame(jsonData)
            observer.notifyEvent(ObservableEvent(
                type = ObservableEventType.STREAMING_NOTIFICATION_RECEIVED,
                properties = mapOf(
                    "notificationType" to "STREAMING_ERROR",
                    "errorCode" to (error?.code?.toString() ?: ""),
                    "errorMessage" to (error?.message ?: ""),
                    "rawData" to (jsonData ?: "")
                )
            ))
            dispatch(PolicyEvent.ErrorFrame(isTokenError = error != null && isTokenError(error)))
        }
    }

    private fun isTokenError(error: ThinStreamingError): Boolean =
        error.statusCode == 401 || error.code in 40140..40149

    private fun handleMessage(event: Map<String, String>) {
        // "Token expired". Handle it before the "real" parser, which would reject it.
        if (event["event"] == "error") {
            handleStreamingErrorFrame(event["data"])
            return
        }

        val jsonData = event["data"] ?: return
        val raw = notificationParser.parseRaw(jsonData) ?: return
        val notification = notificationParser.parse(raw) ?: return

        when (notification) {
            is EvaluationUpdateNotification -> handleEvaluationNotification(notification, jsonData)
            is ThinControlNotification -> {
                // Control/occupancy/error run on a separate tracked job so an eval debounce can't
                // cancel an in-flight push transition, and teardown still tears it down on
                // stop/pause/disconnect.
                controlHandlerJob?.cancel()
                controlHandlerJob = scope.launch {
                    observer.notifyEvent(ObservableEvent(
                        type = ObservableEventType.STREAMING_NOTIFICATION_RECEIVED,
                        properties = mapOf(
                            "notificationType" to notification.controlType.name,
                            "rawData" to jsonData
                        )
                    ))
                    dispatch(controlEvent(notification))
                }
            }
            is ThinOccupancyNotification -> {
                controlHandlerJob?.cancel()
                controlHandlerJob = scope.launch {
                    observer.notifyEvent(ObservableEvent(
                        type = ObservableEventType.STREAMING_NOTIFICATION_RECEIVED,
                        properties = mapOf(
                            "notificationType" to "OCCUPANCY",
                            "rawData" to jsonData
                        )
                    ))
                    if (occupancyTracker.update(notification.channelName, notification.publishers, notification.eventTimestamp)) {
                        dispatch(PolicyEvent.OccupancyChanged(occupancyTracker.isZero()))
                    }
                }
            }
            is ThinStreamingError -> {
                controlHandlerJob?.cancel()
                controlHandlerJob = scope.launch {
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

    /**
     * Eval-notification handling lives entirely in the runtime: the push-down drop gate, the shared
     * change-number CAS advance, unbounded coalescing and the cancel-and-replace of the fetch job.
     * The only policy input is the current [pushUp] snapshot.
     */
    private fun handleEvaluationNotification(notification: EvaluationUpdateNotification, jsonData: String) {
        // While push is down, drop entirely: no observer event and no fetch (polling owns refresh).
        if (!pushUp) {
            return
        }
        observer.notifyEvent(ObservableEvent(
            type = ObservableEventType.STREAMING_NOTIFICATION_RECEIVED,
            properties = mapOf(
                "notificationType" to "EVALUATIONS_UPDATE",
                "rawData" to jsonData
            )
        ))

        // Always advance the shared target change number so an in-flight fetch catches up.
        while (true) {
            val current = evalChangeNumberHolder.get()
            if (notification.changeNumber <= current ||
                evalChangeNumberHolder.compareAndSet(current, notification.changeNumber)
            ) {
                break
            }
        }

        // Coalesce unbounded notifications: if a fetch is already in flight (typically parked in its
        // jitter delay), let it run and pick up the latest change number via the shared holder
        // instead of cancelling and restarting the delay. Bounded notifications carry a key bitmap
        // that must not be lost, so they keep cancel-and-replace semantics.
        val incomingUnbounded =
            (notification.updateStrategy ?: EvaluationUpdateStrategy.UNBOUNDED_FETCH_REQUEST) ==
                EvaluationUpdateStrategy.UNBOUNDED_FETCH_REQUEST
        val canCoalesce =
            incomingUnbounded && inFlightEvalUnbounded && notificationHandlerJob?.isActive == true
        if (canCoalesce) {
            return
        }

        notificationHandlerJob?.cancel()
        inFlightEvalUnbounded = incomingUnbounded
        notificationHandlerJob = scope.launch {
            onEvaluationFetchNotification.invoke(notification)
        }
    }

    private fun controlEvent(notification: ThinControlNotification): PolicyEvent =
        when (notification.controlType) {
            ThinControlNotification.ControlType.STREAMING_RESUMED ->
                PolicyEvent.ControlResumed(notification.eventTimestamp)
            ThinControlNotification.ControlType.STREAMING_PAUSED ->
                PolicyEvent.ControlPaused(notification.eventTimestamp)
            ThinControlNotification.ControlType.STREAMING_DISABLED -> PolicyEvent.ControlDisabled
            ThinControlNotification.ControlType.STREAMING_RESET -> PolicyEvent.ControlReset
        }

    private fun notifySyncModeChanged(to: String, reason: String) {
        observer.notifyEvent(
            ObservableEvent(
                type = ObservableEventType.RUNTIME_SYNC_MODE_CHANGED,
                properties = mapOf("to" to to, "reason" to reason),
            )
        )
    }
}
