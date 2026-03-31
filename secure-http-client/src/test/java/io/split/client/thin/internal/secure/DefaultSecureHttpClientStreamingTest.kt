package io.split.client.thin.internal.secure

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSecureHttpClientStreamingTest {

    @Test
    fun `openStreaming with no callbacks configured is a no-op`() = runTest {
        val (client, _, _) = makeClient()

        // Should not throw
        client.openStreaming(testDefaultTarget)
    }

    @Test
    fun `openStreaming requests credential for active targets`() = runTest {
        val authProvider = FakeAuthProvider()
        val (client, _, _) = makeClient(authProvider = authProvider)

        client.openStreaming(testDefaultTarget)

        assertTrue(authProvider.credentialCallCount > 0)
    }

    @Test
    fun `openStreaming invalidates credentials for a new target`() = runTest {
        val authProvider = FakeAuthProvider()
        val (client, _, _) = makeClient(authProvider = authProvider)

        client.openStreaming(testDefaultTarget)

        assertEquals(1, authProvider.invalidateCallCount)
    }

    @Test
    fun `openStreaming does not invalidate credentials for an already-registered target`() = runTest {
        val authProvider = FakeAuthProvider()
        val (client, _, _) = makeClient(authProvider = authProvider)

        client.openStreaming(testDefaultTarget)
        val countAfterFirst = authProvider.invalidateCallCount
        client.openStreaming(testDefaultTarget) // same target again

        assertEquals(countAfterFirst, authProvider.invalidateCallCount)
    }

    @Test
    fun `closeStreaming calls onStreamingEmpty when no active targets remain`() = runTest {
        var emptyCalled = false
        val (client, _, _) = makeClient(onStreamingEmpty = { emptyCalled = true })

        client.openStreaming(testDefaultTarget)
        client.closeStreaming(testDefaultTarget)

        assertTrue(emptyCalled)
    }

    @Test
    fun `closeStreaming does not call onStreamingEmpty when other targets remain`() = runTest {
        val target2 = EvaluationTarget("user-2", null, null)
        var emptyCalled = false
        val (client, _, _) = makeClient(onStreamingEmpty = { emptyCalled = true })

        client.openStreaming(testDefaultTarget)
        client.openStreaming(target2)
        client.closeStreaming(testDefaultTarget)

        assertFalse(emptyCalled)
    }

    @Test
    fun `credentialForActiveTargets returns credential from authProvider`() = runTest {
        val authProvider = FakeAuthProvider()
        val (client, _, _) = makeClient(authProvider = authProvider)

        client.openStreaming(testDefaultTarget)
        val cred = client.credentialForActiveTargets()

        assertEquals("default-token", cred.token)
    }
}
