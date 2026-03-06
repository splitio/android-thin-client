package io.split.client.thin.internal.auth

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows

class JsonTokenDeserializerTest {

    private val deserializer = JsonTokenDeserializer()

    @Test
    fun `deserialize extracts token`() {
        val credential = deserializer.deserialize(
            """{"token":"jwt-abc","expiresAt":1000000,"pushEnabled":false}"""
        )

        assertEquals("jwt-abc", credential.token)
    }

    @Test
    fun `deserialize extracts expiresAt`() {
        val credential = deserializer.deserialize(
            """{"token":"t","expiresAt":9999999,"pushEnabled":false}"""
        )

        assertEquals(9999999L, credential.expiresAt)
    }

    @Test
    fun `deserialize extracts pushEnabled true`() {
        val credential = deserializer.deserialize(
            """{"token":"t","expiresAt":1,"pushEnabled":true}"""
        )

        assertTrue(credential.pushEnabled)
    }

    @Test
    fun `deserialize extracts pushEnabled false`() {
        val credential = deserializer.deserialize(
            """{"token":"t","expiresAt":1,"pushEnabled":false}"""
        )

        assertFalse(credential.pushEnabled)
    }

    @Test
    fun `deserialize ignores unknown fields`() {
        val credential = deserializer.deserialize(
            """{"token":"t","expiresAt":1,"pushEnabled":false,"extra":"ignored"}"""
        )

        assertEquals("t", credential.token)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `deserialize throws when required field is missing`() {
        assertThrows(MissingFieldException::class.java) {
            deserializer.deserialize("""{"expiresAt":1,"pushEnabled":false}""")
        }
    }
}
