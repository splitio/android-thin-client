package io.split.client.thin.internal.evaluation

import io.split.client.thin.internal.secure.EvaluationFilters
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
    fun `resume after stop does not restart polling`() = runTest {
        val (scheduler, coordinator) = makeScheduler()

        scheduler.start()
        advanceTimeBy(intervalMs + 1)
        scheduler.stop()
        advanceTimeBy(intervalMs * 3)
        val countAfterStop = coordinator.refetchAllCalls.size

        scheduler.resume()
        advanceTimeBy(intervalMs * 3)

        assertEquals(countAfterStop, coordinator.refetchAllCalls.size)

        scheduler.stop()
    }

    @Test
    fun `start re-arms after stop so resume restarts`() = runTest {
        val (scheduler, coordinator) = makeScheduler()

        scheduler.start()
        advanceTimeBy(intervalMs + 1)
        scheduler.stop()
        advanceTimeBy(intervalMs * 3)

        // start re-arms the scheduler
        scheduler.start()
        advanceTimeBy(intervalMs + 1)
        scheduler.pause()
        val countAfterRearm = coordinator.refetchAllCalls.size

        // resume after pause (still armed) restarts
        scheduler.resume()
        advanceTimeBy(intervalMs + 1)

        assertTrue(coordinator.refetchAllCalls.size > countAfterRearm)

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

        assertEquals(EvaluationFilters(), coordinator.refetchAllCalls.last().first)

        scheduler.stop()
    }

    @Test
    fun `polling continues when one refetch throws`() = runTest {
        val coordinator = object : EvaluationFetchCoordinator {
            var calls = 0

            override fun fetchedKeys(): Set<EvaluationKey> = emptySet()

            override suspend fun fetchIfNeeded(
                evalKey: EvaluationKey,
                filters: EvaluationFilters,
                reason: FetchReason,
                delayMs: Long,
                targetChangeNumber: Long?,
            ): Boolean = true

            override suspend fun refetchAll(
                filters: EvaluationFilters,
                reason: FetchReason,
                delayProvider: ((EvaluationKey) -> Long)?,
                keyFilter: (EvaluationKey) -> Boolean,
            ) {
                calls++
                if (calls == 1) throw RuntimeException("refetch failed")
            }

            override fun forget(evalKey: EvaluationKey) {}
        }
        val scheduler = DefaultPollingScheduler(
            fetchCoordinator = coordinator,
            intervalMillis = intervalMs,
            scope = this,
        )

        scheduler.start()
        advanceTimeBy(intervalMs * 2 + 1)

        assertEquals(2, coordinator.calls)

        scheduler.stop()
    }
}
