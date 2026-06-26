package io.split.client.thin.internal.evaluation

import io.split.client.thin.internal.secure.EvaluationFilters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
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
    private val evaluationFilters: EvaluationFilters = EvaluationFilters(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val onPollTrigger: (intervalMillis: Long) -> Unit = {},
) : PollingScheduler {

    private var pollingJob: Job? = null

    // Tracks whether polling is "armed". start() arms, stop() disarms. pause() (background)
    // keeps it armed so resume() (foreground) only re-arms when still armed. This lets the
    // scheduler distinguish "armed but lifecycle-suspended" from "disarmed".
    @Volatile
    private var enabled = false

    override fun start() {
        enabled = true
        if (pollingJob == null || pollingJob?.isCancelled == true) {
            startPolling()
        }
    }

    override fun pause() {
        pollingJob?.cancel()
        pollingJob = null
    }

    override fun resume() {
        if (!enabled) return
        if (pollingJob == null || pollingJob?.isCancelled == true) {
            startPolling()
        }
    }

    override fun stop() {
        enabled = false
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun startPolling() {
        pollingJob = scope.launch {
            while (isActive) {
                delay(intervalMillis)
                onPollTrigger(intervalMillis)
                try {
                    fetchCoordinator.refetchAll(evaluationFilters, FetchReason.PERIODIC)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    // Keep periodic polling alive after one failed iteration.
                }
            }
        }
    }
}
