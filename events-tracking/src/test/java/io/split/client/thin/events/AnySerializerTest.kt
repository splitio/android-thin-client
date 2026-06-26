package io.split.client.thin.events

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class AnySerializerTest {

    @Serializable
    private data class Holder(@Serializable(with = AnySerializer::class) val v: Any)

    private val json = Json

    @Test
    fun `serializes string`() {
        assertTrue(json.encodeToString(Holder("hello")).contains("\"v\":\"hello\""))
    }

    @Test
    fun `serializes boolean`() {
        assertTrue(json.encodeToString(Holder(true)).contains("\"v\":true"))
    }

    @Test
    fun `serializes small number as plain decimal`() {
        assertTrue(json.encodeToString(Holder(0.0003)).contains("\"v\":0.0003"))
    }

    @Test
    fun `serializes non-finite number as null`() {
        assertTrue(json.encodeToString(Holder(Double.NaN)).contains("\"v\":null"))
    }

    @Test
    fun `serializes other type via toString`() {
        assertTrue(json.encodeToString(Holder(StringBuilder("x"))).contains("\"v\":\"x\""))
    }

    @Test(expected = UnsupportedOperationException::class)
    fun `deserialize unsupported`() {
        json.decodeFromString(AnySerializer, "\"x\"")
    }
}
