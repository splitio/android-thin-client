package io.split.client.thin.internal.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThinNotificationParserErrorFrameTest {

    private val parser = ThinNotificationParser()

    @Test
    fun `parseErrorFrame parses a token-expired Ably frame`() {
        val result = parser.parseErrorFrame(
            """{"code":40142,"statusCode":401,"message":"Token expired","href":"https://x/error/40142"}"""
        )

        requireNotNull(result)
        assertEquals(40142, result.code)
        assertEquals(401, result.statusCode)
        assertEquals("Token expired", result.message)
    }

    @Test
    fun `parseErrorFrame tolerates missing fields`() {
        val result = parser.parseErrorFrame("""{"message":"something"}""")

        requireNotNull(result)
        assertEquals(-1, result.code)
        assertNull(result.statusCode)
        assertEquals("something", result.message)
    }

    @Test
    fun `parseErrorFrame returns null on malformed JSON`() {
        assertNull(parser.parseErrorFrame("not-json"))
    }

    @Test
    fun `parseErrorFrame returns null on null data`() {
        assertNull(parser.parseErrorFrame(null))
    }
}
