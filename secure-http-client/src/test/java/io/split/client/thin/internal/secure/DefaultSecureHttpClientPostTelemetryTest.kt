package io.split.client.thin.internal.secure

import io.split.client.thin.http.RequestCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultSecureHttpClientPostTelemetryTest {

    @Test
    fun `sends POST to telemetryUrl`() = runTest {
        val (client, _, http) = makeClient()

        client.postTelemetry("telemetry-payload")

        assertEquals(testTelemetryUrl, http.lastRequest?.uri?.toString())
    }

    @Test
    fun `uses TELEMETRY category`() = runTest {
        val (client, _, http) = makeClient()

        client.postTelemetry("telemetry-payload")

        assertEquals(RequestCategory.TELEMETRY, http.lastCategory)
    }

    @Test
    fun `sends payload as body`() = runTest {
        val (client, _, http) = makeClient()

        client.postTelemetry("my-telemetry-data")

        assertEquals("my-telemetry-data", http.lastRequest?.body)
    }
}
