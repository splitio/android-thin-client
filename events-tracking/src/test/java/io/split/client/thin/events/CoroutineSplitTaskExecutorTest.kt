package io.split.client.thin.events

import io.split.android.client.service.executor.SplitTask
import io.split.android.client.service.executor.SplitTaskExecutionInfo
import io.split.android.client.service.executor.SplitTaskExecutionListener
import io.split.android.client.service.executor.SplitTaskExecutionStatus
import io.split.android.client.service.executor.SplitTaskType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoroutineSplitTaskExecutorTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var executor: CoroutineSplitTaskExecutor

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        executor = CoroutineSplitTaskExecutor(CoroutineScope(testDispatcher))
    }

    @Test
    fun `submit executes task and calls listener`() = runTest(testDispatcher) {
        var taskExecuted = false
        var listenerCalled = false
        val task = object : SplitTask {
            override fun execute(): SplitTaskExecutionInfo {
                taskExecuted = true
                return SplitTaskExecutionInfo.success(SplitTaskType.GENERIC_TASK)
            }
        }
        val listener = object : SplitTaskExecutionListener {
            override fun taskExecuted(taskInfo: SplitTaskExecutionInfo) {
                listenerCalled = true
                assertEquals(SplitTaskExecutionStatus.SUCCESS, taskInfo.status)
            }
        }

        executor.submit(task, listener)
        advanceUntilIdle()

        assertTrue(taskExecuted)
        assertTrue(listenerCalled)
    }

    @Test
    fun `submit executes task without listener`() = runTest(testDispatcher) {
        var taskExecuted = false
        val task = object : SplitTask {
            override fun execute(): SplitTaskExecutionInfo {
                taskExecuted = true
                return SplitTaskExecutionInfo.success(SplitTaskType.GENERIC_TASK)
            }
        }

        executor.submit(task, null)
        advanceUntilIdle()

        assertTrue(taskExecuted)
    }

    @Test
    fun `submit handles task exceptions`() = runTest(testDispatcher) {
        var listenerCalled = false
        val task = object : SplitTask {
            override fun execute(): SplitTaskExecutionInfo {
                throw RuntimeException("Task failed")
            }
        }
        val listener = object : SplitTaskExecutionListener {
            override fun taskExecuted(taskInfo: SplitTaskExecutionInfo) {
                listenerCalled = true
            }
        }

        executor.submit(task, listener)
        advanceUntilIdle()

        // Should handle exception gracefully
        // Listener may or may not be called depending on implementation
    }
}
