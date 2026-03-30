package io.split.client.thin.internal.persistence.domain.evaluation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttributesSerializerTest {

    private val serializer = AttributesSerializer()

    @Test
    fun `serialize and deserialize empty map`() {
        val attrs = emptyMap<String, Any?>()
        val json = serializer.serialize(attrs)
        val result = serializer.deserialize(json)
        assertEquals(attrs, result)
    }

    @Test
    fun `serialize and deserialize string attribute`() {
        val attrs = mapOf<String, Any?>("name" to "alice")
        val json = serializer.serialize(attrs)
        val result = serializer.deserialize(json)
        assertEquals("alice", result["name"])
    }

    @Test
    fun `serialize and deserialize boolean attribute`() {
        val attrs = mapOf<String, Any?>("flag" to true)
        val json = serializer.serialize(attrs)
        val result = serializer.deserialize(json)
        assertEquals(true, result["flag"])
    }

    @Test
    fun `serialize and deserialize int attribute`() {
        val attrs = mapOf<String, Any?>("count" to 42)
        val json = serializer.serialize(attrs)
        val result = serializer.deserialize(json)
        // JSON numbers deserialize to Long or Double
        assertEquals(42L, result["count"])
    }

    @Test
    fun `serialize and deserialize double attribute`() {
        val attrs = mapOf<String, Any?>("score" to 3.14)
        val json = serializer.serialize(attrs)
        val result = serializer.deserialize(json)
        assertEquals(3.14, result["score"] as Double, 0.001)
    }

    @Test
    fun `serialize and deserialize null attribute`() {
        val attrs = mapOf<String, Any?>("nullKey" to null)
        val json = serializer.serialize(attrs)
        val result = serializer.deserialize(json)
        assert(result.containsKey("nullKey"))
        assertNull(result["nullKey"])
    }

    @Test
    fun `serialize and deserialize multiple attributes`() {
        val attrs = mapOf<String, Any?>("str" to "hello", "num" to 10L, "bool" to false)
        val json = serializer.serialize(attrs)
        val result = serializer.deserialize(json)
        assertEquals("hello", result["str"])
        assertEquals(10L, result["num"])
        assertEquals(false, result["bool"])
    }
}
