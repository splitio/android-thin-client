package io.split.client.thin.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventTrackerTest {

    @Test
    fun `track returns true for valid event`() {
        val eventTracker = EventTracker.create()

        val result = eventTracker.tracker.track(
            "user-1",
            "user",
            "purchase",
            9.99,
            null,
            true,
        )

        assertTrue(result)
    }

    @Test
    fun `invalid event type is rejected`() {
        val eventTracker = EventTracker.create()

        val result = eventTracker.tracker.track(
            "user-1",
            "user",
            "invalid event type with spaces",
            0.0,
            null,
            true,
        )

        assertFalse(result)
    }

    @Test
    fun `empty key is rejected`() {
        val eventTracker = EventTracker.create()

        val result = eventTracker.tracker.track(
            "",
            "user",
            "purchase",
            0.0,
            null,
            true,
        )

        assertFalse(result)
    }

    @Test
    fun `tracking disabled after enableTracking false`() {
        val eventTracker = EventTracker.create()
        eventTracker.tracker.enableTracking(false)

        val result = eventTracker.tracker.track(
            "user-1",
            "user",
            "purchase",
            0.0,
            null,
            true
        )

        assertFalse(result)
    }

    @Test
    fun `onEventPush is called when event is valid`() {
        var pushedEventType: String? = null
        val eventTracker = EventTracker.create(
            onEventPush = { event -> pushedEventType = event.eventType }
        )

        eventTracker.tracker.track("user-1", "user", "purchase", 9.99, null, true)

        assertEquals("purchase", pushedEventType)
    }
}
