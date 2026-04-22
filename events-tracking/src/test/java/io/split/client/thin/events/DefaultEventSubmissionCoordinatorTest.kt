package io.split.client.thin.events

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultEventSubmissionCoordinatorTest {

    @Test
    fun `flush runs after previously submitted tasks on the same dispatcher`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val order = mutableListOf<String>()

        val coordinator = DefaultEventSubmissionCoordinator(
            scope = scope,
            task = {
                order.add("flush")
                io.split.android.client.service.executor.SplitTaskExecutionInfo.success(
                    io.split.android.client.service.executor.SplitTaskType.GENERIC_TASK
                )
            }
        )

        // Simulate a push task queued before flush
        scope.launch { order.add("push") }

        coordinator.flush()

        advanceUntilIdle()
        assertEquals(listOf("push", "flush"), order)
    }
}
