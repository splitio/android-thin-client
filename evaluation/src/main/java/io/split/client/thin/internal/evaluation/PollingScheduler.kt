package io.split.client.thin.internal.evaluation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface PollingScheduler {
    fun start()
    fun pause()
    fun resume()
    fun stop()
}

class DefaultPollingScheduler(
    private val fetchCoordinator: EvaluationFetchCoordinator,
    private val intervalMillis: Long,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val onPollTrigger: (intervalMillis: Long) -> Unit = {},
) : PollingScheduler {

    private var pollingJob: Job? = null

    override fun start() {
        if (pollingJob == null || pollingJob?.isCancelled == true) {
            startPolling()
        }
    }

    override fun pause() {
        pollingJob?.cancel()
        pollingJob = null
    }

    override fun resume() {
        if (pollingJob == null || pollingJob?.isCancelled == true) {
            startPolling()
        }
    }

    override fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun startPolling() {
        pollingJob = scope.launch {
            while (isActive) {
                delay(intervalMillis)
                onPollTrigger(intervalMillis)
                fetchCoordinator.refetchAll(null, FetchReason.PERIODIC)
            }
        }
    }
}
