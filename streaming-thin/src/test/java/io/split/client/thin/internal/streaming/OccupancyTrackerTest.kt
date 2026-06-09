package io.split.client.thin.internal.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class OccupancyTrackerTest {

    @Test
    fun `present by default after construction`() {
        val tracker = OccupancyTracker()

        assertFalse(tracker.isZero())
    }

    @Test
    fun `present by default after reset`() {
        val tracker = OccupancyTracker()
        tracker.update("channel-1", 0, 1)

        tracker.reset()

        assertFalse(tracker.isZero())
    }

    @Test
    fun `reports zero when single channel drops to zero`() {
        val tracker = OccupancyTracker()
        tracker.update("channel-1", 2, 1)

        tracker.update("channel-1", 0, 2)

        assertTrue(tracker.isZero())
    }

    @Test
    fun `reports not zero while there are publishers`() {
        val tracker = OccupancyTracker()

        tracker.update("channel-1", 2, 1)

        assertFalse(tracker.isZero())
    }

    @Test
    fun `reports zero immediately when first occupancy says zero`() {
        val tracker = OccupancyTracker()

        tracker.update("channel-1", 0, 1)

        // Absolute state: a known channel reporting 0 publishers means zero
        assertTrue(tracker.isZero())
    }

    @Test
    fun `total occupancy aggregates across channels`() {
        val tracker = OccupancyTracker()

        tracker.update("channel-1", 2, 1)
        tracker.update("channel-2", 1, 1)
        // channel-1 drops to zero but channel-2 still has publishers -> not zero overall
        tracker.update("channel-1", 0, 2)

        assertFalse(tracker.isZero())
    }

    @Test
    fun `ignores stale per-channel timestamps`() {
        val tracker = OccupancyTracker()
        tracker.update("channel-1", 2, 10)

        // Older timestamp for the same channel is ignored
        val applied = tracker.update("channel-1", 0, 5)

        assertFalse(applied)
        assertFalse(tracker.isZero())
    }

    @Test
    fun `applies newer per-channel timestamps`() {
        val tracker = OccupancyTracker()
        tracker.update("channel-1", 2, 10)

        val applied = tracker.update("channel-1", 0, 20)

        assertTrue(applied)
        assertTrue(tracker.isZero())
    }

    @Test
    fun `equal timestamp is treated as stale`() {
        val tracker = OccupancyTracker()
        tracker.update("channel-1", 2, 10)

        val applied = tracker.update("channel-1", 0, 10)

        assertFalse(applied)
        assertFalse(tracker.isZero())
    }

    @Test
    fun `concurrent updates and resets do not throw`() {
        val tracker = OccupancyTracker()
        val threads = 8
        val iterations = 5_000
        val pool = Executors.newFixedThreadPool(threads)
        val startLatch = CountDownLatch(1)
        val error = AtomicReference<Throwable?>(null)

        repeat(threads) { t ->
            pool.submit {
                try {
                    startLatch.await()
                    repeat(iterations) { i ->
                        tracker.update("channel-${(t + i) % 16}", i % 3, i.toLong())
                        tracker.isZero()
                        if (i % 100 == 0) tracker.reset()
                    }
                } catch (e: Throwable) {
                    error.compareAndSet(null, e)
                }
            }
        }

        startLatch.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        assertEquals(null, error.get())
    }
}
