package io.split.client.thin.events

import io.split.android.client.service.executor.SplitTaskExecutionInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DefaultEventSubmissionCoordinator(
    private val scope: CoroutineScope,
    private val task: suspend () -> SplitTaskExecutionInfo
) : EventSubmissionCoordinator {
    private val mutex = Mutex()

    override fun triggerSubmission(reason: EventFlushReason) {
        scope.launch {
            if (!mutex.tryLock()) return@launch
            try {
                task()
            } finally {
                mutex.unlock()
            }
        }
    }

    override suspend fun flush() {
        mutex.withLock {
            task()
        }
    }

    override fun stop() {
        scope.cancel()
    }
}
