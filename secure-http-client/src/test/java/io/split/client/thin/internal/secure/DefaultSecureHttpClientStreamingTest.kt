package io.split.client.thin.internal.secure

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSecureHttpClientStreamingTest {

    @Test
    fun `openStreaming throws when controller is null`() = runTest {
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
    fun `closeStreaming throws when controller is null`() = runTest {
        val (client, _, _) = makeClient()
        var thrown: Throwable? = null

        try {
            client.closeStreaming()
        } catch (e: UnsupportedOperationException) {
            thrown = e
        }

        assertTrue("Expected UnsupportedOperationException", thrown is UnsupportedOperationException)
    }

    @Test
    fun `openStreaming delegates to controller`() = runTest {
        val (client, _, _) = makeClient()
        val controller = FakeStreamingController()
        client.streamingController = controller

        client.openStreaming(testDefaultTarget)

        assertEquals(1, controller.startCallCount)
    }

    @Test
    fun `closeStreaming delegates to controller`() = runTest {
        val (client, _, _) = makeClient()
        val controller = FakeStreamingController()
        client.streamingController = controller

        client.closeStreaming()

        assertEquals(1, controller.stopCallCount)
    }
}

private class FakeStreamingController : StreamingController {
    var startCallCount = 0
    var stopCallCount = 0
    var pauseCallCount = 0
    var resumeCallCount = 0

    override suspend fun start() {
        startCallCount++
    }

    override suspend fun stop() {
        stopCallCount++
    }

    override suspend fun pause() {
        pauseCallCount++
    }

    override suspend fun resume() {
        resumeCallCount++
    }
}
