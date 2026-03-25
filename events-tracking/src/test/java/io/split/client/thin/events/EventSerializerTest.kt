package io.split.client.thin.events

import io.split.android.client.tracker.TrackerEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventSerializerTest {

    @Test
    fun `serialize single event`() {
        val event = createEvent(
            key = "user-123",
            trafficType = "user",
            eventType = "page_view",
            value = 1.5,
            timestamp = 1234567890L,
            properties = mapOf("browser" to "chrome", "version" to 100)
        )

        val json = EventSerializer.serialize(listOf(event))

        assertTrue(json.contains("\"key\":\"user-123\""))
        assertTrue(json.contains("\"trafficTypeName\":\"user\""))
        assertTrue(json.contains("\"eventTypeId\":\"page_view\""))
        assertTrue(json.contains("\"value\":1.5"))
        assertTrue(json.contains("\"timestamp\":1234567890"))
        assertTrue(json.contains("\"properties\":{"))
        assertTrue(json.contains("\"browser\":\"chrome\""))
    }

    @Test
    fun `serialize multiple events`() {
        val events = listOf(
            createEvent(key = "user-1", eventType = "event1", timestamp = 1000L),
            createEvent(key = "user-2", eventType = "event2", timestamp = 2000L)
        )

        val json = EventSerializer.serialize(events)

        assertTrue(json.startsWith("["))
        assertTrue(json.endsWith("]"))
        assertTrue(json.contains("\"key\":\"user-1\""))
        assertTrue(json.contains("\"key\":\"user-2\""))
        assertTrue(json.contains("\"eventTypeId\":\"event1\""))
        assertTrue(json.contains("\"eventTypeId\":\"event2\""))
    }

    @Test
    fun `serialize empty list`() {
        val json = EventSerializer.serialize(emptyList())

        assertEquals("[]", json)
    }

    @Test
    fun `serialize event with null properties`() {
        val event = createEvent(properties = null)

        val json = EventSerializer.serialize(listOf(event))

        assertTrue(json.contains("\"properties\":null"))
    }

    @Test
    fun `serialize event with empty properties`() {
        val event = createEvent(properties = emptyMap())

        val json = EventSerializer.serialize(listOf(event))

        assertTrue(json.contains("\"properties\":{}"))
    }

    private fun createEvent(
        key: String = "test-key",
        trafficType: String = "user",
        eventType: String = "test-event",
        value: Double = 0.0,
        timestamp: Long = System.currentTimeMillis(),
        properties: Map<String, Any>? = emptyMap()
    ): TrackerEvent {
        return TrackerEvent().apply {
            this.key = key
            this.trafficType = trafficType
            this.eventType = eventType
            this.value = value
            this.timestamp = timestamp
            this.properties = properties
            this.sizeInBytes = 100
        }
    }
}
