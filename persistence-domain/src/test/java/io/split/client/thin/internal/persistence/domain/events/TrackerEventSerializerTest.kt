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

    @Test
    fun `serialize list property preserves structure as JSON array`() {
        val event = makeEvent(properties = mapOf("tags" to listOf("a", "b")))
        val json = serializer.serialize(event)
        val result = serializer.deserialize(json)
        @Suppress("UNCHECKED_CAST")
        val tags = result.properties?.get("tags") as? List<*>
        assertEquals(listOf("a", "b"), tags)
    }

    @Test
    fun `serialize map property preserves structure as JSON object`() {
        val event = makeEvent(properties = mapOf("meta" to mapOf("k" to 1L)))
        val json = serializer.serialize(event)
        val result = serializer.deserialize(json)
        @Suppress("UNCHECKED_CAST")
        val meta = result.properties?.get("meta") as? Map<*, *>
        assertEquals(1L, meta?.get("k"))
    }

    @Test
    fun `deserialize event with null property value does not throw`() {
        // A null value stored in the JSON properties map must not crash deserialization
        val json = """{"trafficType":"user","eventType":"buy","key":"u","value":1.0,"timestamp":1000,"properties":{"x":null,"y":"ok"}}"""
        val result = serializer.deserialize(json)
        assertEquals("ok", result.properties?.get("y"))
        // null entry must be absent from the result map
        assertEquals(false, result.properties?.containsKey("x"))
    }

    @Test
    fun `round-trip event with mixed-type properties preserves all non-null values`() {
        val event = makeEvent(
            properties = mapOf(
                "tags" to listOf("a", "b"),
                "meta" to mapOf("k" to 1L),
                "count" to 5L,
                "flag" to true,
                "label" to "hello",
            )
        )
        val json = serializer.serialize(event)
        val result = serializer.deserialize(json)
        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf("a", "b"), result.properties?.get("tags") as? List<*>)
        @Suppress("UNCHECKED_CAST")
        assertEquals(1L, (result.properties?.get("meta") as? Map<*, *>)?.get("k"))
        assertEquals(5L, result.properties?.get("count"))
        assertEquals(true, result.properties?.get("flag"))
        assertEquals("hello", result.properties?.get("label"))
    }

    @Test
    fun `serialize event with null property drops the entry`() {
        val event = makeEvent(properties = mapOf("a" to null as Any?, "b" to 1L) as Map<String, Any>)
        val json = serializer.serialize(event)
        val result = serializer.deserialize(json)
        assertEquals(mapOf("b" to 1L), result.properties)
    }

    @Test
    fun `legacy row with embedded JsonNull decodes with that entry filtered`() {
        val json = """{"trafficType":"user","eventType":"buy","key":"u","value":1.0,"timestamp":1000,"properties":{"a":null,"b":1}}"""
        val result = serializer.deserialize(json)
        assertEquals(mapOf("b" to 1L), result.properties)
    }
}
