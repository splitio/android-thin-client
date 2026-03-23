package io.split.client.thin.internal.auth

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows

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
            """{"token":"$jwtWith9999999","pushEnabled":false}"""
        )

        assertEquals(jwtWith9999999, credential.token)
    }

    @Test
    fun `deserialize extracts exp from JWT payload`() {
        val credential = deserializer.deserialize(
            """{"token":"$jwtWith9999999","pushEnabled":false}"""
        )

        assertEquals(9999999L, credential.expiresAt)
    }

    @Test
    fun `deserialize extracts pushEnabled true`() {
        val credential = deserializer.deserialize(
            """{"token":"$jwtWith1","pushEnabled":true}"""
        )

        assertTrue(credential.pushEnabled)
    }

    @Test
    fun `deserialize extracts pushEnabled false`() {
        val credential = deserializer.deserialize(
            """{"token":"$jwtWith1","pushEnabled":false}"""
        )

        assertFalse(credential.pushEnabled)
    }

    @Test
    fun `deserialize ignores unknown fields`() {
        val credential = deserializer.deserialize(
            """{"token":"$jwtWith1","pushEnabled":false,"extra":"ignored"}"""
        )

        assertEquals(jwtWith1, credential.token)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `deserialize throws when required field is missing`() {
        assertThrows(MissingFieldException::class.java) {
            deserializer.deserialize("""{"pushEnabled":false}""")
        }
    }
}
