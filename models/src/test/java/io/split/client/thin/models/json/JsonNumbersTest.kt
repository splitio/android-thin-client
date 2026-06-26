package io.split.client.thin.models.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonNumbersTest {

    @Test
    fun `toPlainJsonElement converts small double without scientific notation`() {
        val result = 0.0003.toPlainJsonElement()
        assertEquals("0.0003", result.toString())
    }

    @Test
    fun `toPlainJsonElement converts very small double without scientific notation`() {
        val result = 1.23e-10.toPlainJsonElement()
        assertEquals("0.000000000123", result.toString())
    }

    @Test
    fun `toPlainJsonElement converts large double without scientific notation`() {
        val result = 1e21.toPlainJsonElement()
        assertEquals("1000000000000000000000", result.toString())
    }

    @Test
    fun `toPlainJsonElement converts int to plain json`() {
        val result = 42.toPlainJsonElement()
        assertEquals("42", result.toString())
    }

    @Test
    fun `toPlainJsonElement converts long to plain json`() {
        val result = 42L.toPlainJsonElement()
        assertEquals("42", result.toString())
    }

    @Test
    fun `toPlainJsonElement converts float to plain json`() {
        val result = 1.5f.toPlainJsonElement()
        assertEquals("1.5", result.toString())
    }

    @Test
    fun `toPlainJsonElement converts zero to plain json`() {
        val result = 0.toPlainJsonElement()
        assertEquals("0", result.toString())
    }

    @Test
    fun `toPlainJsonElement converts negative small double without scientific notation`() {
        val result = (-0.0001).toPlainJsonElement()
        assertEquals("-0.0001", result.toString())
    }

    @Test
    fun `toPlainJsonElement converts Double NaN to JsonNull`() {
        val result = Double.NaN.toPlainJsonElement()
        assertEquals(JsonNull, result)
    }

    @Test
    fun `toPlainJsonElement converts Double POSITIVE_INFINITY to JsonNull`() {
        val result = Double.POSITIVE_INFINITY.toPlainJsonElement()
        assertEquals(JsonNull, result)
    }

    @Test
    fun `toPlainJsonElement converts Double NEGATIVE_INFINITY to JsonNull`() {
        val result = Double.NEGATIVE_INFINITY.toPlainJsonElement()
        assertEquals(JsonNull, result)
    }

    @Test
    fun `toPlainJsonElement converts Float NaN to JsonNull`() {
        val result = Float.NaN.toPlainJsonElement()
        assertEquals(JsonNull, result)
    }

    @Test
    fun `toPlainJsonElement converts Float POSITIVE_INFINITY to JsonNull`() {
        val result = Float.POSITIVE_INFINITY.toPlainJsonElement()
        assertEquals(JsonNull, result)
    }

    @Test
    fun `toPlainJsonElement converts Float NEGATIVE_INFINITY to JsonNull`() {
        val result = Float.NEGATIVE_INFINITY.toPlainJsonElement()
        assertEquals(JsonNull, result)
    }

    @Test
    fun `toPlainJsonElement converts zero double to plain json`() {
        val result = 0.0.toPlainJsonElement()
        // Accept either "0" or "0.0" as valid plain decimal
        val str = result.toString()
        assert(str == "0" || str == "0.0") { "Expected '0' or '0.0', got '$str'" }
    }

    @Test
    fun `PlainDoubleSerializer serializes small double without scientific notation`() {
        val json = Json.encodeToString(PlainDoubleSerializer, 0.0003)
        assertEquals("0.0003", json)
    }

    @Test
    fun `PlainDoubleSerializer serializes NaN as null`() {
        val json = Json.encodeToString(PlainDoubleSerializer, Double.NaN)
        assertEquals("null", json)
    }

    @Test
    fun `PlainDoubleSerializer serializes positive infinity as null`() {
        val json = Json.encodeToString(PlainDoubleSerializer, Double.POSITIVE_INFINITY)
        assertEquals("null", json)
    }

    @Test
    fun `PlainDoubleSerializer serializes regular double correctly`() {
        val json = Json.encodeToString(PlainDoubleSerializer, 1.5)
        assertEquals("1.5", json)
    }

    @Test
    fun `PlainDoubleSerializer deserializes double correctly`() {
        val value = Json.decodeFromString(PlainDoubleSerializer, "0.0003")
        assertEquals(0.0003, value, 0.0)
    }

    @Test
    fun `PlainDoubleSerializer serializes very small double without scientific notation`() {
        val json = Json.encodeToString(PlainDoubleSerializer, 1.23e-10)
        assertEquals("0.000000000123", json)
    }

    @Test
    fun `PlainDoubleSerializer serializes large double without scientific notation`() {
        val json = Json.encodeToString(PlainDoubleSerializer, 1e21)
        assertEquals("1000000000000000000000", json)
    }

    @Test
    fun `PlainDoubleSerializer deserializes null as NaN`() {
        val value = Json.decodeFromString(PlainDoubleSerializer, "null")
        assert(value.isNaN()) { "Expected NaN, got $value" }
    }

    @Test
    fun `toPlainJsonElement converts Short to plain json`() {
        val result = (42.toShort()).toPlainJsonElement()
        assertEquals("42", result.toString())
    }

    @Test
    fun `toPlainJsonElement converts Byte to plain json`() {
        val result = (42.toByte()).toPlainJsonElement()
        assertEquals("42", result.toString())
    }

    @Test
    fun `toPlainJsonElement converts BigInteger to plain json`() {
        val result = java.math.BigInteger.valueOf(123456789).toPlainJsonElement()
        assertEquals("123456789", result.toString())
    }

    @Test
    fun `toPlainJsonElement converts BigDecimal to plain json`() {
        val result = java.math.BigDecimal("123.456").toPlainJsonElement()
        assertEquals("123.456", result.toString())
    }

    @Test
    fun `toPlainJsonElement converts custom Number type to plain json`() {
        // Test the generic fallback path with a custom Number subclass
        val customNumber = object : Number() {
            override fun toByte(): Byte = 42
            override fun toDouble(): Double = 1.23e-10
            override fun toFloat(): Float = 1.23e-10f
            override fun toInt(): Int = 0
            override fun toLong(): Long = 0
            override fun toShort(): Short = 0
        }
        val result = customNumber.toPlainJsonElement()
        assertEquals("0.000000000123", result.toString())
    }

    @Test
    fun `toPlainJsonElement converts custom non-finite Number to JsonNull`() {
        // Test the generic fallback path with non-finite value
        val customNumber = object : Number() {
            override fun toByte(): Byte = 0
            override fun toDouble(): Double = Double.POSITIVE_INFINITY
            override fun toFloat(): Float = Float.POSITIVE_INFINITY
            override fun toInt(): Int = 0
            override fun toLong(): Long = 0
            override fun toShort(): Short = 0
        }
        val result = customNumber.toPlainJsonElement()
        assertEquals(JsonNull, result)
    }
}
