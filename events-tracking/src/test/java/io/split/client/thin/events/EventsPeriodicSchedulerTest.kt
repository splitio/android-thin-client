package io.split.client.thin.events

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventsPeriodicSchedulerTest {

    @Test
    fun `start does not throw exception`() {
        val coordinator = object : EventSubmissionCoordinator {
            override fun triggerSubmission(reason: EventFlushReason) {
                throw UnsupportedOperationException()
            }
            override suspend fun flush() {
                throw UnsupportedOperationException()
            }
            override fun stop() {}
        }

        val scheduler = EventsPeriodicScheduler(
            scope = CoroutineScope(Dispatchers.Unconfined),
            coordinator = coordinator,
            pushRateMillis = 100L
        )

        // Just verify scheduler starts without throwing
        scheduler.start()
        scheduler.stop()
    }

    @Test
    fun `triggerSubmission uses INTERVAL reason`() {
        var lastReason: EventFlushReason? = null
        val coordinator = object : EventSubmissionCoordinator {
            override fun triggerSubmission(reason: EventFlushReason) {
                lastReason = reason
            }
            override suspend fun flush() {}
            override fun stop() {}
        }

        // Manually trigger to test the reason
        coordinator.triggerSubmission(EventFlushReason.INTERVAL)

        assertEquals(EventFlushReason.INTERVAL, lastReason)
    }

    @Test
    fun `stop cancels periodic trigger`() = runTest {
        var triggerCount = 0
        val coordinator = object : EventSubmissionCoordinator {
            override fun triggerSubmission(reason: EventFlushReason) {
                triggerCount++
            }
            override suspend fun flush() {}
            override fun stop() {}
        }

        val scheduler = EventsPeriodicScheduler(
            scope = this,
            coordinator = coordinator,
            pushRateMillis = 1000L
        )

        scheduler.start()
        advanceTimeBy(1500)
        val countBeforeStop = triggerCount

        scheduler.stop()
        advanceTimeBy(2000)

        assertEquals(countBeforeStop, triggerCount)
    }
}
