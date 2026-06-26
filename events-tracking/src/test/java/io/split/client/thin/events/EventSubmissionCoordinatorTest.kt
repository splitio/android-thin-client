package io.split.client.thin.events

import io.split.android.client.service.executor.SplitTaskExecutionInfo
import io.split.android.client.service.executor.SplitTaskExecutionStatus
import io.split.android.client.service.executor.SplitTaskType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventSubmissionCoordinatorTest {

    private val testDispatcher = StandardTestDispatcher()
    private var taskExecutionCount = 0
    private lateinit var coordinator: EventSubmissionCoordinator

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        taskExecutionCount = 0
        coordinator = DefaultEventSubmissionCoordinator(
            scope = CoroutineScope(testDispatcher),
            task = {
                taskExecutionCount++
                SplitTaskExecutionInfo.success(SplitTaskType.GENERIC_TASK)
            }
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `triggerSubmission executes task`() = runTest(testDispatcher) {
        coordinator.triggerSubmission(EventFlushReason.QUEUE)
        advanceUntilIdle()

        assertEquals(1, taskExecutionCount)
    }

    @Test
    fun `triggerSubmission drops requests while task is running`() = runTest(testDispatcher) {
        coordinator = DefaultEventSubmissionCoordinator(
            scope = CoroutineScope(testDispatcher),
            task = {
                taskExecutionCount++
                delay(1000)
                SplitTaskExecutionInfo.success(SplitTaskType.GENERIC_TASK)
            }
        )

        // Trigger first submission
        coordinator.triggerSubmission(EventFlushReason.QUEUE)
        // Don't advance - task is still running
        coordinator.triggerSubmission(EventFlushReason.INTERVAL) // Should be dropped (mutex locked)
        coordinator.triggerSubmission(EventFlushReason.INTERVAL) // Should be dropped (mutex locked)

        // Now let all tasks complete
        advanceUntilIdle()

        // Only the first task should have executed
        assertEquals(1, taskExecutionCount)
    }

    @Test
    fun `flush waits for task completion`() = runTest(testDispatcher) {
        var taskCompleted = false

        coordinator = DefaultEventSubmissionCoordinator(
            scope = CoroutineScope(testDispatcher),
            task = {
                delay(100)
                taskCompleted = true
                SplitTaskExecutionInfo.success(SplitTaskType.GENERIC_TASK)
            }
        )

        coordinator.flush()

        assertTrue(taskCompleted)
    }

    @Test
    fun `flush executes task and waits`() = runTest(testDispatcher) {
        coordinator.flush()

        assertEquals(1, taskExecutionCount)
    }

    @Test
    fun `multiple sequential triggers execute successfully`() = runTest(testDispatcher) {
        coordinator.triggerSubmission(EventFlushReason.QUEUE)
        advanceUntilIdle()

        coordinator.triggerSubmission(EventFlushReason.INTERVAL)
        advanceUntilIdle()

        assertEquals(2, taskExecutionCount)
    }
}
