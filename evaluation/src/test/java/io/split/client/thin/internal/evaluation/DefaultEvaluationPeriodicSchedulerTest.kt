package io.split.client.thin.internal.evaluation

import io.split.client.thin.Key
import io.split.client.thin.Target
import io.split.client.thin.internal.secure.EvaluationFilters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultEvaluationPeriodicSchedulerTest {

    private val target = Target(Key("user-1"))
    private val evalKey = target.toEvaluationKey()
    private val intervalMs = 60_000L

    private fun TestScope.makeScheduler(
        coordinator: FakeEvaluationFetchCoordinator = FakeEvaluationFetchCoordinator(),
        interval: Long = intervalMs,
    ): Pair<DefaultEvaluationPeriodicScheduler, FakeEvaluationFetchCoordinator> {
        val scheduler = DefaultEvaluationPeriodicScheduler(
            fetchCoordinator = coordinator,
            intervalMillis = interval,
            scope = this,
        )
        return scheduler to coordinator
    }

    @Test
    fun `start begins polling after interval`() = runTest {
        val (scheduler, coordinator) = makeScheduler()

        scheduler.start(target, null)
        advanceTimeBy(intervalMs + 1)

        assertTrue(coordinator.fetchCalls.isNotEmpty())
        assertEquals(FetchReason.PERIODIC, coordinator.fetchCalls[0].third)
        assertEquals(evalKey, coordinator.fetchCalls[0].first)

        scheduler.stop()
    }

    @Test
    fun `start does not poll immediately before first interval`() = runTest {
        val (scheduler, coordinator) = makeScheduler()

        scheduler.start(target, null)
        advanceTimeBy(intervalMs - 1)

        assertTrue(coordinator.fetchCalls.isEmpty())

        scheduler.stop()
    }

    @Test
    fun `stop cancels polling`() = runTest {
        val (scheduler, coordinator) = makeScheduler()

        scheduler.start(target, null)
        advanceTimeBy(intervalMs + 1)
        val countAfterFirst = coordinator.fetchCalls.size
        scheduler.stop()
        advanceTimeBy(intervalMs * 3)

        // No additional polls after stop
        assertEquals(countAfterFirst, coordinator.fetchCalls.size)
    }

    @Test
    fun `pause stops polling`() = runTest {
        val (scheduler, coordinator) = makeScheduler()

        scheduler.start(target, null)
        advanceTimeBy(intervalMs + 1)
        val countAfterFirst = coordinator.fetchCalls.size
        scheduler.pause()
        advanceTimeBy(intervalMs * 3)

        assertEquals(countAfterFirst, coordinator.fetchCalls.size)

        scheduler.stop()
    }

    @Test
    fun `resume restarts polling after pause`() = runTest {
        val (scheduler, coordinator) = makeScheduler()

        scheduler.start(target, null)
        advanceTimeBy(intervalMs + 1)
        scheduler.pause()
        advanceTimeBy(intervalMs * 3)
        val countBeforeResume = coordinator.fetchCalls.size
        scheduler.resume()
        advanceTimeBy(intervalMs + 1)

        assertTrue(coordinator.fetchCalls.size > countBeforeResume)

        scheduler.stop()
    }

    @Test
    fun `updateTarget affects next poll`() = runTest {
        val (scheduler, coordinator) = makeScheduler()
        val newTarget = Target(Key("user-2"))
        val newEvalKey = newTarget.toEvaluationKey()

        scheduler.start(target, null)
        scheduler.updateTarget(newTarget, null)
        advanceTimeBy(intervalMs + 1)

        val lastCall = coordinator.fetchCalls.lastOrNull()
        assertEquals(newEvalKey, lastCall?.first)

        scheduler.stop()
    }

    @Test
    fun `uses FetchReason PERIODIC`() = runTest {
        val (scheduler, coordinator) = makeScheduler()

        scheduler.start(target, null)
        advanceTimeBy(intervalMs + 1)

        assertEquals(FetchReason.PERIODIC, coordinator.fetchCalls.last().third)

        scheduler.stop()
    }

    @Test
    fun `polls at regular intervals`() = runTest {
        val (scheduler, coordinator) = makeScheduler()

        scheduler.start(target, null)
        advanceTimeBy(intervalMs * 3 + 1)

        assertEquals(3, coordinator.fetchCalls.size)

        scheduler.stop()
    }
}
