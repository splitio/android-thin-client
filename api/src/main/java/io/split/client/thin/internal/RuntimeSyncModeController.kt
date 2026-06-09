package io.split.client.thin.internal

import io.split.client.thin.internal.evaluation.PollingScheduler
import io.split.client.thin.internal.observer.ObservableEventType
import io.split.client.thin.internal.streaming.StreamingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class RuntimeSyncModeController(
    private val scope: CoroutineScope,
    private val initialSyncMode: String = "UNKNOWN",
    private val onRuntimeSyncModeChanged: (type: String, properties: Map<String, String>) -> Unit = { _, _ -> },
) {
    private var singleSync = false
    private var pollingScheduler: PollingScheduler? = null
    private var streamingManager: StreamingManager? = null

    @Synchronized
    fun isSingleSync(): Boolean = singleSync

    @Synchronized
    fun setPollingScheduler(scheduler: PollingScheduler) {
        pollingScheduler = scheduler
        if (singleSync) scheduler.stop()
    }

    @Synchronized
    fun setStreamingManager(manager: StreamingManager) {
        streamingManager = manager
        if (singleSync) {
            scope.launch { manager.stopAll() }
        }
    }

    fun startPollingIfAllowed(schedulerProvider: () -> PollingScheduler) {
        if (!isSingleSync()) schedulerProvider().start()
    }

    fun resumePolling(scheduler: PollingScheduler) {
        if (!isSingleSync()) scheduler.resume()
    }

    fun startStreamingIfAllowed(startTrigger: () -> Unit) {
        if (!isSingleSync()) startTrigger()
    }

    fun resumeStreaming(manager: StreamingManager) {
        if (!isSingleSync()) manager.resume()
    }

    @Synchronized
    fun switchToSingleSync() {
        if (singleSync) return
        singleSync = true
        onRuntimeSyncModeChanged(
            ObservableEventType.RUNTIME_SYNC_MODE_CHANGED,
            mapOf(
                "from" to initialSyncMode,
                "to" to "SINGLE_SYNC",
                "reason" to "AUTH_UNAUTHORIZED",
            )
        )
        pollingScheduler?.stop()
        streamingManager?.let { manager ->
            scope.launch { manager.stopAll() }
        }
    }
}
