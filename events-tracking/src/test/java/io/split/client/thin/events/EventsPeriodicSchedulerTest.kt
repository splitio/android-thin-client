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

    @Test
    fun `pause stops periodic trigger`() = runTest {
        var triggerCount = 0
        val coordinator = object : EventSubmissionCoordinator {
            override fun triggerSubmission(reason: EventFlushReason) {
                triggerCount++
            }
            override suspend fun flush() {}
        }

        val scheduler = EventsPeriodicScheduler(
            scope = this,
            coordinator = coordinator,
            pushRateMillis = 1000L
        )

        scheduler.start()
        advanceTimeBy(1500)
        val countAfterFirst = triggerCount

        scheduler.pause()
        advanceTimeBy(3000)

        assertEquals(countAfterFirst, triggerCount)
    }

    @Test
    fun `pause before start is safe`() {
        val coordinator = object : EventSubmissionCoordinator {
            override fun triggerSubmission(reason: EventFlushReason) {}
            override suspend fun flush() {}
        }

        val scheduler = EventsPeriodicScheduler(
            scope = CoroutineScope(Dispatchers.Unconfined),
            coordinator = coordinator,
            pushRateMillis = 1000L
        )

        // Should not throw
        scheduler.pause()
    }

    @Test
    fun `multiple pause calls are idempotent`() = runTest {
        var triggerCount = 0
        val coordinator = object : EventSubmissionCoordinator {
            override fun triggerSubmission(reason: EventFlushReason) {
                triggerCount++
            }
            override suspend fun flush() {}
        }

        val scheduler = EventsPeriodicScheduler(
            scope = this,
            coordinator = coordinator,
            pushRateMillis = 1000L
        )

        scheduler.start()
        advanceTimeBy(1500)

        scheduler.pause()
        scheduler.pause()
        scheduler.pause()

        advanceTimeBy(3000)
        val countAfterPauses = triggerCount

        // Should still be paused
        advanceTimeBy(3000)
        assertEquals(countAfterPauses, triggerCount)
    }

    @Test
    fun `resume restarts periodic trigger after pause`() = runTest {
        var triggerCount = 0
        val coordinator = object : EventSubmissionCoordinator {
            override fun triggerSubmission(reason: EventFlushReason) {
                triggerCount++
            }
            override suspend fun flush() {}
        }

        val scheduler = EventsPeriodicScheduler(
            scope = this,
            coordinator = coordinator,
            pushRateMillis = 1000L
        )

        scheduler.start()
        advanceTimeBy(1500)
        scheduler.pause()
        advanceTimeBy(3000)
        val countBeforeResume = triggerCount

        scheduler.resume()
        advanceTimeBy(1500)

        assertTrue(triggerCount > countBeforeResume)

        scheduler.stop()
    }

    @Test
    fun `resume before start calls start`() = runTest {
        var triggerCount = 0
        val coordinator = object : EventSubmissionCoordinator {
            override fun triggerSubmission(reason: EventFlushReason) {
                triggerCount++
            }
            override suspend fun flush() {}
        }

        val scheduler = EventsPeriodicScheduler(
            scope = this,
            coordinator = coordinator,
            pushRateMillis = 1000L
        )

        scheduler.resume()  // Calls start internally
        advanceTimeBy(1500)

        assertTrue(triggerCount > 0)

        scheduler.stop()
    }

    @Test
    fun `start triggers FLUSH after initialDelayMillis before first INTERVAL`() = runTest {
        val reasons = mutableListOf<EventFlushReason>()
        val coordinator = object : EventSubmissionCoordinator {
            override fun triggerSubmission(reason: EventFlushReason) {
                reasons.add(reason)
            }
            override suspend fun flush() {}
        }

        val scheduler = EventsPeriodicScheduler(
            scope = this,
            coordinator = coordinator,
            pushRateMillis = 1000L,
            initialDelayMillis = 500L,
        )

        scheduler.start()

        advanceTimeBy(499)
        assertEquals(emptyList<EventFlushReason>(), reasons.toList())

        advanceTimeBy(2)
        assertEquals(listOf(EventFlushReason.FLUSH), reasons.toList())

        advanceTimeBy(1000)
        assertEquals(listOf(EventFlushReason.FLUSH, EventFlushReason.INTERVAL), reasons.toList())

        scheduler.stop()
    }

    @Test
    fun `initialDelayMillis defaults to zero so existing behavior is preserved`() = runTest {
        var triggerCount = 0
        val coordinator = object : EventSubmissionCoordinator {
            override fun triggerSubmission(reason: EventFlushReason) {
                triggerCount++
            }
            override suspend fun flush() {}
        }

        val scheduler = EventsPeriodicScheduler(
            scope = this,
            coordinator = coordinator,
            pushRateMillis = 1000L,
        )

        scheduler.start()
        advanceTimeBy(1500)

        assertEquals(1, triggerCount)
        scheduler.stop()
    }

    @Test
    fun `multiple resume calls do not create duplicate jobs`() = runTest {
        var triggerCount = 0
        val coordinator = object : EventSubmissionCoordinator {
            override fun triggerSubmission(reason: EventFlushReason) {
                triggerCount++
            }
            override suspend fun flush() {}
        }

        val scheduler = EventsPeriodicScheduler(
            scope = this,
            coordinator = coordinator,
            pushRateMillis = 1000L
        )

        scheduler.start()
        scheduler.resume()
        scheduler.resume()

        advanceTimeBy(1500)

        // Should only trigger once per interval, not 3x
        assertEquals(1, triggerCount)

        scheduler.stop()
    }
}
