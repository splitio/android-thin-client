package io.split.client.thin.internal.sdkevents

import io.harness.events.EventHandler
import io.split.client.thin.SplitEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SplitEventDeliveryTest {

    @Test
    fun `deliver invokes handler on scope`() = runTest {
        val scope = TestScope(testScheduler)
        val delivery = SplitEventDelivery(scope)
        var called = false
        val handler = EventHandler<SplitEvent, Any?> { _, _ -> called = true }

        delivery.deliver(handler, SplitEvent.SDK_READY, null)
        scope.advanceUntilIdle()

        assertTrue(called)
    }

    @Test
    fun `deliver passes event and metadata to handler`() = runTest {
        val scope = TestScope(testScheduler)
        val delivery = SplitEventDelivery(scope)
        var receivedEvent: SplitEvent? = null
        var receivedMetadata: Any? = "sentinel"
        val handler = EventHandler<SplitEvent, Any?> { event, metadata ->
            receivedEvent = event
            receivedMetadata = metadata
        }

        delivery.deliver(handler, SplitEvent.SDK_UPDATE, null)
        scope.advanceUntilIdle()

        assertEquals(SplitEvent.SDK_UPDATE, receivedEvent)
        assertEquals(null, receivedMetadata)
    }

    @Test
    fun `handler exception does not propagate out of deliver`() = runTest {
        val scope = TestScope(testScheduler)
        val delivery = SplitEventDelivery(scope)
        val handler = EventHandler<SplitEvent, Any?> { _, _ -> throw RuntimeException("boom") }

        delivery.deliver(handler, SplitEvent.SDK_READY, null)
        scope.advanceUntilIdle()
        // No exception thrown — fault isolation is preserved.
    }

    @Test
    fun `cancelled scope does not invoke handler`() = runTest {
        val scope = TestScope(testScheduler)
        val delivery = SplitEventDelivery(scope)
        var called = false
        val handler = EventHandler<SplitEvent, Any?> { _, _ -> called = true }

        scope.cancel()
        delivery.deliver(handler, SplitEvent.SDK_READY, null)
        scope.advanceUntilIdle()

        assertTrue(!called)
    }
}
