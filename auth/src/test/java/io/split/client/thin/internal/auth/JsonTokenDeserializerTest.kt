package io.split.client.thin.internal.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTokenDeserializerTest {

    private val deserializer = JsonTokenDeserializer(
        base64Decoder = { input ->
            val padded = input.padEnd((input.length + 3) / 4 * 4, '=')
            java.util.Base64.getUrlDecoder().decode(padded)
        }
    )

    // payload {"exp":9999999} → base64url eyJleHAiOjk5OTk5OTl9
    private val jwtWith9999999 = "a.eyJleHAiOjk5OTk5OTl9.s"
    // payload {"exp":1} → base64url eyJleHAiOjF9
    private val jwtWith1 = "a.eyJleHAiOjF9.s"

    @Test
    fun `deserialize extracts token`() {
        val credential = deserializer.deserialize(
            """{"token":"$jwtWith9999999","config":{"streaming":{"enabled":false,"delay":60}}}"""
        )

        assertEquals(jwtWith9999999, credential.token)
    }

    @Test
    fun `deserialize extracts exp from JWT payload`() {
        val credential = deserializer.deserialize(
            """{"token":"$jwtWith9999999","config":{"streaming":{"enabled":false,"delay":60}}}"""
        )

        assertEquals(9999999L, credential.expiresAt)
    }

    @Test
    fun `deserialize extracts pushEnabled true`() {
        val credential = deserializer.deserialize(
            """{"token":"$jwtWith1","config":{"streaming":{"enabled":true,"delay":60}}}"""
        )

        assertTrue(credential.pushEnabled)
    }

    @Test
    fun `deserialize extracts pushEnabled false`() {
        val credential = deserializer.deserialize(
            """{"token":"$jwtWith1","config":{"streaming":{"enabled":false,"delay":60}}}"""
        )

        assertFalse(credential.pushEnabled)
    }

    @Test
    fun `deserialize defaults pushEnabled to false when config is absent`() {
        val credential = deserializer.deserialize("""{"token":"$jwtWith1"}""")

        assertFalse(credential.pushEnabled)
        assertEquals(60L, credential.connDelaySeconds)
    }

    @Test
    fun `deserialize extracts connDelaySeconds from config`() {
        val credential = deserializer.deserialize(
            """{"token":"$jwtWith1","config":{"streaming":{"enabled":true,"delay":30}}}"""
        )

        assertEquals(30L, credential.connDelaySeconds)
    }

    @Test
    fun `deserialize defaults when streaming is absent in config`() {
        val credential = deserializer.deserialize(
            """{"token":"$jwtWith1","config":{}}"""
        )

        assertFalse(credential.pushEnabled)
        assertEquals(60L, credential.connDelaySeconds)
    }

    @Test
    fun `deserialize ignores unknown fields`() {
        val credential = deserializer.deserialize(
            """{"token":"$jwtWith1","config":{"streaming":{"enabled":false,"delay":60}},"extra":"ignored"}"""
        )

        assertEquals(jwtWith1, credential.token)
    }

    @Test
    fun `deserialize sets expiresAt to 0 when token is empty`() {
        val credential = deserializer.deserialize("""{"token":""}""")

        assertEquals(0L, credential.expiresAt)
    }

    @Test
    fun `deserialize sets expiresAt to 0 and token to empty when token field is absent`() {
        val credential = deserializer.deserialize("""{"config":{"streaming":{"enabled":false,"delay":60}}}""")

        assertEquals("", credential.token)
        assertEquals(0L, credential.expiresAt)
    }

    @Test
    fun `deserialize sets expiresAt to 0 and token to empty when token is null`() {
        val credential = deserializer.deserialize("""{"token":null}""")

        assertEquals("", credential.token)
        assertEquals(0L, credential.expiresAt)
    }

    @Test
    fun `deserialize sets expiresAt to MAX_VALUE when token has no dot separator`() {
        val credential = deserializer.deserialize("""{"token":"nodots"}""")

        assertEquals(Long.MAX_VALUE, credential.expiresAt)
    }

    @Test
    fun `deserialize sets expiresAt to MAX_VALUE when payload is not valid base64`() {
        val credential = deserializer.deserialize("""{"token":"a.!!!.s"}""")

        assertEquals(Long.MAX_VALUE, credential.expiresAt)
    }

    @Test
    fun `deserialize sets expiresAt to MAX_VALUE when payload has no exp field`() {
        // payload {"iss":"test"} → base64url eyJpc3MiOiJ0ZXN0In0
        val jwtNoExp = "a.eyJpc3MiOiJ0ZXN0In0.s"
        val credential = deserializer.deserialize("""{"token":"$jwtNoExp"}""")

        assertEquals(Long.MAX_VALUE, credential.expiresAt)
    }
}
