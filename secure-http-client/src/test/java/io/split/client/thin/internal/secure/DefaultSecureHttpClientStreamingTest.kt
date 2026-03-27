package io.split.client.thin.internal.secure

import io.split.client.thin.internal.streaming.StreamingManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSecureHttpClientStreamingTest {

    @Test
    fun `openStreaming adds target matchingKey to active set and starts streaming`() = runTest {
        val streamingManager = FakeStreamingManager()
        val (client, _, _) = makeClient(streamingManager = streamingManager)

        client.openStreaming(testDefaultTarget)

        assertEquals(1, streamingManager.startCallCount)
    }

    @Test
    fun `openStreaming with no streaming manager configured is a no-op`() = runTest {
        val (client, _, _) = makeClient(streamingManager = null)

        // Should not throw
        client.openStreaming(testDefaultTarget)
    }

    @Test
    fun `closeStreaming removes target and stops streaming when no more active targets`() = runTest {
        val streamingManager = FakeStreamingManager()
        val (client, _, _) = makeClient(streamingManager = streamingManager)

        client.openStreaming(testDefaultTarget)
        client.closeStreaming(testDefaultTarget)

        assertEquals(1, streamingManager.stopAllCallCount)
    }

    @Test
    fun `closeStreaming reconnects streaming when other targets remain active`() = runTest {
        val target2 = EvaluationTarget("user-2", null, null)
        val streamingManager = FakeStreamingManager()
        val (client, _, _) = makeClient(streamingManager = streamingManager)

        client.openStreaming(testDefaultTarget)
        client.openStreaming(target2)
        client.closeStreaming(testDefaultTarget)

        // Should have reconnected (not stopped completely)
        assertTrue(streamingManager.startCallCount >= 2)
        assertEquals(0, streamingManager.stopAllCallCount)
    }

    @Test
    fun `openStreaming triggers credential for active targets`() = runTest {
        val authProvider = FakeAuthProvider()
        val (client, _, _) = makeClient(authProvider = authProvider, streamingManager = FakeStreamingManager())

        client.openStreaming(testDefaultTarget)

        assertTrue(authProvider.credentialCallCount > 0)
    }
}

internal class FakeStreamingManager : StreamingManager {
    var startCallCount = 0
    var stopCallCount = 0
    var pauseCallCount = 0
    var resumeCallCount = 0
    var stopAllCallCount = 0
    var lastTokenProvider: (suspend () -> String)? = null

    override suspend fun start() {
        startCallCount++
    }

    override suspend fun stop() {
        stopCallCount++
    }

    override fun pause() {
        pauseCallCount++
    }

    override fun resume() {
        resumeCallCount++
    }

    override suspend fun stopAll() {
        stopAllCallCount++
    }
}
