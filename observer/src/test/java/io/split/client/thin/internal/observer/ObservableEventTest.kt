package io.split.client.thin.internal.observer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObservableEventTest {

    @Test
    fun `ObservableEvent has payload field with null default`() {
        val event = ObservableEvent("test_event")

        assertNull(event.payload)
    }

    @Test
    fun `ObservableEvent payload can be set to a value`() {
        val payloadData = mapOf("key" to "value")
        val event = ObservableEvent("test_event", payload = payloadData)

        assertEquals(payloadData, event.payload)
    }

    @Test
    fun `ObservableEvent with all fields populated`() {
        val properties = mapOf("prop1" to "value1")
        val payload = listOf(1, 2, 3)
        val timestamp = 1000L

        val event = ObservableEvent(
            type = "test_event",
            properties = properties,
            payload = payload,
            timestamp = timestamp
        )

        assertEquals("test_event", event.type)
        assertEquals(properties, event.properties)
        assertEquals(payload, event.payload)
        assertEquals(timestamp, event.timestamp)
    }

    @Test
    fun `ObservableEvent payload is included in equality`() {
        val event1 = ObservableEvent("test", payload = "data1")
        val event2 = ObservableEvent("test", payload = "data1")
        val event3 = ObservableEvent("test", payload = "data2")

        assertEquals(event1, event2)
        assert(event1 != event3)
    }
}
