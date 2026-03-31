package io.split.client.thin.internal.evaluation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultPollingSchedulerTest {

    private val intervalMs = 60_000L

    private fun TestScope.makeScheduler(
        coordinator: FakeEvaluationFetchCoordinator = FakeEvaluationFetchCoordinator(),
        interval: Long = intervalMs,
    ): Pair<DefaultPollingScheduler, FakeEvaluationFetchCoordinator> {
        val scheduler = DefaultPollingScheduler(
            fetchCoordinator = coordinator,
            intervalMillis = interval,
            scope = this,
        )
        return scheduler to coordinator
    }

    @Test
    fun `start begins polling after interval`() = runTest {
        val (scheduler, coordinator) = makeScheduler()

        scheduler.start()
        advanceTimeBy(intervalMs + 1)

        assertTrue(coordinator.refetchAllCalls.isNotEmpty())
        assertEquals(FetchReason.PERIODIC, coordinator.refetchAllCalls[0].second)

        scheduler.stop()
    }

    @Test
    fun `start does not poll immediately before first interval`() = runTest {
        val (scheduler, coordinator) = makeScheduler()

        scheduler.start()
        advanceTimeBy(intervalMs - 1)

        assertTrue(coordinator.refetchAllCalls.isEmpty())

        scheduler.stop()
    }

    @Test
    fun `stop cancels polling`() = runTest {
        val (scheduler, coordinator) = makeScheduler()

        scheduler.start()
        advanceTimeBy(intervalMs + 1)
        val countAfterFirst = coordinator.refetchAllCalls.size
        scheduler.stop()
        advanceTimeBy(intervalMs * 3)

        // No additional polls after stop
        assertEquals(countAfterFirst, coordinator.refetchAllCalls.size)
    }

    @Test
    fun `pause stops polling`() = runTest {
        val (scheduler, coordinator) = makeScheduler()

        scheduler.start()
        advanceTimeBy(intervalMs + 1)
        val countAfterFirst = coordinator.refetchAllCalls.size
        scheduler.pause()
        advanceTimeBy(intervalMs * 3)

        assertEquals(countAfterFirst, coordinator.refetchAllCalls.size)

        scheduler.stop()
    }

    @Test
    fun `resume restarts polling after pause`() = runTest {
        val (scheduler, coordinator) = makeScheduler()

        scheduler.start()
        advanceTimeBy(intervalMs + 1)
        scheduler.pause()
        advanceTimeBy(intervalMs * 3)
        val countBeforeResume = coordinator.refetchAllCalls.size
        scheduler.resume()
        advanceTimeBy(intervalMs + 1)

        assertTrue(coordinator.refetchAllCalls.size > countBeforeResume)

        scheduler.stop()
    }

    @Test
    fun `uses FetchReason PERIODIC`() = runTest {
        val (scheduler, coordinator) = makeScheduler()

        scheduler.start()
        advanceTimeBy(intervalMs + 1)

        assertEquals(FetchReason.PERIODIC, coordinator.refetchAllCalls.last().second)

        scheduler.stop()
    }

    @Test
    fun `polls at regular intervals`() = runTest {
        val (scheduler, coordinator) = makeScheduler()

        scheduler.start()
        advanceTimeBy(intervalMs * 3 + 1)

        assertEquals(3, coordinator.refetchAllCalls.size)

        scheduler.stop()
    }

    @Test
    fun `calls refetchAll with null filters`() = runTest {
        val (scheduler, coordinator) = makeScheduler()

        scheduler.start()
        advanceTimeBy(intervalMs + 1)

        assertEquals(null, coordinator.refetchAllCalls.last().first)

        scheduler.stop()
    }
}
