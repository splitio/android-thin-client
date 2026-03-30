package io.split.client.thin.internal.persistence.domain.events

import io.split.android.client.tracker.TrackerEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackerEventSerializerTest {

    private val serializer = TrackerEventSerializer()

    private fun makeEvent(
        trafficType: String = "user",
        eventType: String = "purchase",
        key: String = "user1",
        value: Double = 1.0,
        timestamp: Long = 1000L,
        properties: Map<String, Any>? = null
    ): TrackerEvent {
        val event = TrackerEvent()
        event.trafficType = trafficType
        event.eventType = eventType
        event.key = key
        event.value = value
        event.timestamp = timestamp
        event.properties = properties
        event.sizeInBytes = 0
        return event
    }

    @Test
    fun `serialize and deserialize basic event`() {
        val event = makeEvent()
        val json = serializer.serialize(event)
        val result = serializer.deserialize(json)
        assertEquals("user", result.trafficType)
        assertEquals("purchase", result.eventType)
        assertEquals("user1", result.key)
        assertEquals(1.0, result.value, 0.001)
        assertEquals(1000L, result.timestamp)
        assertNull(result.properties)
    }

    @Test
    fun `serialize and deserialize event with properties`() {
        val event = makeEvent(
            properties = mapOf("color" to "red", "count" to 5)
        )
        val json = serializer.serialize(event)
        val result = serializer.deserialize(json)
        assertEquals("red", result.properties?.get("color"))
        // int may deserialize as Long
        assertEquals(5L, result.properties?.get("count"))
    }

    @Test
    fun `serialize and deserialize event with null properties`() {
        val event = makeEvent(properties = null)
        val json = serializer.serialize(event)
        val result = serializer.deserialize(json)
        assertNull(result.properties)
    }

    @Test
    fun `serialize filters out null values from properties map`() {
        // Java Map<String, Object> can contain null values; they should be dropped on serialize
        @Suppress("UNCHECKED_CAST")
        val propsWithNulls = mapOf("color" to "red", "nullKey" to null) as Map<String, Any?>
        val event = makeEvent(properties = propsWithNulls as Map<String, Any>)
        val json = serializer.serialize(event)
        val result = serializer.deserialize(json)
        assertEquals("red", result.properties?.get("color"))
        assertEquals(false, result.properties?.containsKey("nullKey"))
    }

    @Test
    fun `serialize and deserialize event with non-null only properties`() {
        val event = makeEvent(
            properties = mapOf("str" to "value", "num" to 42L, "bool" to true, "dbl" to 3.14)
        )
        val json = serializer.serialize(event)
        val result = serializer.deserialize(json)
        assertEquals("value", result.properties?.get("str"))
        assertEquals(42L, result.properties?.get("num"))
        assertEquals(true, result.properties?.get("bool"))
        assertEquals(3.14, result.properties?.get("dbl") as Double, 0.001)
    }

    @Test
    fun `serialize and deserialize preserves double value`() {
        val event = makeEvent(value = 99.99)
        val json = serializer.serialize(event)
        val result = serializer.deserialize(json)
        assertEquals(99.99, result.value, 0.001)
    }
}
