package io.split.client.thin.events

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class EventsPeriodicScheduler(
    private val scope: CoroutineScope,
    private val coordinator: EventSubmissionCoordinator,
    private val pushRateMillis: Long
) {
    private var job: Job? = null

    fun start() {
        job = scope.launch {
            while (isActive) {
                delay(pushRateMillis)
                coordinator.triggerSubmission(EventFlushReason.INTERVAL)
            }
        }
    }

    fun pause() {
        job?.cancel()
        job = null
    }

    fun resume() {
        if (job == null || job?.isCancelled == true) {
            start()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
