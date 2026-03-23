package io.split.client.thin.internal.secure

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSecureHttpClientStreamingTest {

    @Test
    fun `openStreaming throws UnsupportedOperationException`() = runTest {
        val (client, _, _) = makeClient()
        var thrown: Throwable? = null

        try {
            client.openStreaming(testDefaultTarget)
        } catch (e: UnsupportedOperationException) {
            thrown = e
        }

        assertTrue("Expected UnsupportedOperationException", thrown is UnsupportedOperationException)
    }

    @Test
    fun `closeStreaming throws UnsupportedOperationException`() = runTest {
        val (client, _, _) = makeClient()
        var thrown: Throwable? = null

        try {
            client.closeStreaming()
        } catch (e: UnsupportedOperationException) {
            thrown = e
        }

        assertTrue("Expected UnsupportedOperationException", thrown is UnsupportedOperationException)
    }
}
