package io.split.client.thin.events

import io.split.android.client.service.executor.SplitTask
import io.split.android.client.service.executor.SplitTaskBatchItem
import io.split.android.client.service.executor.SplitTaskExecutionListener
import io.split.android.client.service.executor.SplitTaskExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class CoroutineSplitTaskExecutor(
    private val scope: CoroutineScope
) : SplitTaskExecutor {

    override fun submit(task: SplitTask, executionListener: SplitTaskExecutionListener?) {
        scope.launch {
            try {
                val result = task.execute()
                executionListener?.taskExecuted(result)
            } catch (e: Exception) {
                // Handle exceptions gracefully - log or ignore
            }
        }
    }

    override fun schedule(
        task: SplitTask,
        initialDelayInSecs: Long,
        periodInSecs: Long,
        executionListener: SplitTaskExecutionListener?
    ): String {
        // Not used by RecorderSyncHelperImpl
        return ""
    }

    override fun schedule(
        task: SplitTask,
        initialDelayInSecs: Long,
        executionListener: SplitTaskExecutionListener?
    ): String {
        // Not used by RecorderSyncHelperImpl
        return ""
    }

    override fun executeSerially(tasks: List<SplitTaskBatchItem>) {
        // Not used by RecorderSyncHelperImpl
    }

    override fun pause() {
        // Not used by RecorderSyncHelperImpl
    }

    override fun resume() {
        // Not used by RecorderSyncHelperImpl
    }

    override fun stopTask(taskId: String) {
        // Not used by RecorderSyncHelperImpl
    }

    override fun stop() {
        // Not used by RecorderSyncHelperImpl
    }

    override fun submitOnMainThread(splitTask: SplitTask) {
        // Not used by RecorderSyncHelperImpl
    }
}
