package io.split.client.thin.internal.streaming

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class SseJwtParserTest {

    // Injects a JVM base64url decoder so tests don't need Android runtime
    private val parser = SseJwtParser(
        base64Decoder = { encoded ->
            try {
                Base64.getUrlDecoder().decode(encoded).toString(Charsets.UTF_8)
            } catch (e: Exception) {
                null
            }
        }
    )

    // Builds a fake JWT with the given payload JSON as the middle segment
    private fun fakeJwt(payloadJson: String): String {
        val encodedPayload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payloadJson.toByteArray(Charsets.UTF_8))
        return "header.$encodedPayload.signature"
    }

    private fun capabilityJson(vararg pairs: Pair<String, List<String>>): String {
        val inner = pairs.joinToString(",") { (ch, perms) ->
            val permList = perms.joinToString(",") { "\"$it\"" }
            "\"$ch\":[$permList]"
        }
        // The x-ably-capability value is itself a JSON-encoded string
        return "{$inner}"
    }

    @Test
    fun `parse returns regular channels without prefix`() {
        val capability = capabilityJson("evaluations_abc" to listOf("subscribe"))
        val jwt = fakeJwt("""{"x-ably-capability":"${capability.replace("\"", "\\\"")}"}""")

        val result = parser.parse(jwt)

        assertEquals(listOf("evaluations_abc"), result)
    }

    @Test
    fun `parse adds occupancy prefix to channels with publishers metadata`() {
        val capability = capabilityJson(
            "control_pri" to listOf("subscribe", "channel-metadata:publishers")
        )
        val jwt = fakeJwt("""{"x-ably-capability":"${capability.replace("\"", "\\\"")}"}""")

        val result = parser.parse(jwt)

        assertEquals(listOf("[?occupancy=metrics.publishers]control_pri"), result)
    }

    @Test
    fun `parse handles mixed channels correctly`() {
        val capability = capabilityJson(
            "evaluations_abc" to listOf("subscribe"),
            "control_pri" to listOf("subscribe", "channel-metadata:publishers")
        )
        val jwt = fakeJwt("""{"x-ably-capability":"${capability.replace("\"", "\\\"")}"}""")

        val result = parser.parse(jwt)

        assertTrue(result.contains("evaluations_abc"))
        assertTrue(result.contains("[?occupancy=metrics.publishers]control_pri"))
        assertEquals(2, result.size)
    }

    @Test
    fun `parse returns empty list when x-ably-capability is missing`() {
        val jwt = fakeJwt("""{"iat":1000,"exp":9999}""")

        val result = parser.parse(jwt)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse returns empty list for token without dots`() {
        val result = parser.parse("notavalidtoken")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse returns empty list when base64 decode fails`() {
        val failingParser = SseJwtParser(base64Decoder = { null })

        val result = failingParser.parse("header.payload.signature")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse returns empty list when payload JSON is malformed`() {
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("not valid json {".toByteArray())
        val result = parser.parse("header.$encoded.signature")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse handles token with only two segments`() {
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"x-ably-capability":"{\"ch\":[\"subscribe\"]}"}""".toByteArray())
        val result = parser.parse("header.$encoded")

        assertEquals(listOf("ch"), result)
    }
}
