package io.split.client.thin.events

import io.split.android.client.tracker.TrackerEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `serialize event with null value produces value null`() {
        val event = createEvent(value = null)

        val json = EventSerializer.serialize(listOf(event))

        assertTrue("expected value:null in $json", json.contains("\"value\":null"))
        assertFalse("must not contain value:0 when null passed", json.contains("\"value\":0"))
    }

    @Test
    fun `serialize event with null property values preserves them as null`() {
        @Suppress("UNCHECKED_CAST")
        val props = mapOf("a" to "x", "b" to null) as Map<String, Any>
        val event = createEvent(properties = props)

        val json = EventSerializer.serialize(listOf(event))

        assertTrue("property 'a' must be present in $json", json.contains("\"a\""))
        assertTrue("property 'b' with null value must be present as null in $json", json.contains("\"b\":null"))
    }

    @Test
    fun `serialize property values of all supported types`() {
        @Suppress("UNCHECKED_CAST")
        val props = mapOf(
            "str" to "hello",
            "num" to 42,
            "bool" to true,
            "nil" to null,
            "other" to StringBuilder("custom"),
        ) as Map<String, Any>
        val event = createEvent(properties = props)

        val json = EventSerializer.serialize(listOf(event))

        assertTrue(json.contains("\"str\":\"hello\""))
        assertTrue(json.contains("\"num\":42"))
        assertTrue(json.contains("\"bool\":true"))
        assertTrue(json.contains("\"nil\":null"))
        assertTrue(json.contains("\"other\":\"custom\""))
    }

    @Test
    fun `serialize small value uses plain decimal not scientific`() {
        val event = createEvent(value = 0.0003)

        val json = EventSerializer.serialize(listOf(event))

        assertTrue("expected value:0.0003 in $json", json.contains("\"value\":0.0003"))
        assertFalse("must not use scientific notation in $json", json.contains("E"))
    }

    @Test
    fun `serialize small property uses plain decimal not scientific`() {
        val event = createEvent(properties = mapOf("discount" to 0.0003))

        val json = EventSerializer.serialize(listOf(event))

        assertTrue("expected discount:0.0003 in $json", json.contains("\"discount\":0.0003"))
    }

    @Test
    fun `serialize NaN value produces value null`() {
        val event = createEvent(value = Double.NaN)

        val json = EventSerializer.serialize(listOf(event))

        assertTrue("expected value:null in $json", json.contains("\"value\":null"))
    }

    @Test
    fun `serialize non-finite property produces null`() {
        val event = createEvent(properties = mapOf("discount" to Double.POSITIVE_INFINITY))

        val json = EventSerializer.serialize(listOf(event))

        assertTrue("expected discount:null in $json", json.contains("\"discount\":null"))
    }

    private fun createEvent(
        key: String = "test-key",
        trafficType: String = "user",
        eventType: String = "test-event",
        value: Double? = 0.0,
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
