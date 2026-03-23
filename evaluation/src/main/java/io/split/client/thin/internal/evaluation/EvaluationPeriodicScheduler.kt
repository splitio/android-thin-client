package io.split.client.thin.internal.evaluation

import io.split.client.thin.Target
import io.split.client.thin.internal.secure.EvaluationFilters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

interface EvaluationPeriodicScheduler {
    fun start(target: Target, filters: EvaluationFilters?)
    fun pause()
    fun resume()
    fun stop()
    fun updateTarget(target: Target, filters: EvaluationFilters?)
}

class DefaultEvaluationPeriodicScheduler(
    private val fetchCoordinator: EvaluationFetchCoordinator,
    private val intervalMillis: Long,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val onPollTrigger: (intervalMillis: Long) -> Unit = {},
) : EvaluationPeriodicScheduler {

    private val currentTarget = AtomicReference<Pair<Target, EvaluationFilters?>?>(null)
    private var pollingJob: Job? = null

    override fun start(target: Target, filters: EvaluationFilters?) {
        currentTarget.set(target to filters)
        startPolling()
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
        currentTarget.set(null)
    }

    override fun updateTarget(target: Target, filters: EvaluationFilters?) {
        currentTarget.set(target to filters)
    }

    private fun startPolling() {
        pollingJob = scope.launch {
            while (isActive) {
                delay(intervalMillis)
                onPollTrigger(intervalMillis)
                val (target, filters) = currentTarget.get() ?: continue
                val evalKey = target.toEvaluationKey()
                fetchCoordinator.fetchIfNeeded(evalKey, filters, FetchReason.PERIODIC)
            }
        }
    }
}
