package io.split.client.thin.internal.secure

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSecureHttpClientStreamingTest {

    @Test
    fun `openStreaming calls onStreamingTargetsChanged with updated active set`() = runTest {
        var capturedTargets: Set<EvaluationTarget>? = null
        val (client, _, _) = makeClient(
            onStreamingTargetsChanged = { targets -> capturedTargets = targets }
        )

        client.openStreaming(testDefaultTarget)

        assertEquals(setOf(testDefaultTarget), capturedTargets)
    }

    @Test
    fun `openStreaming with no callbacks configured is a no-op`() = runTest {
        val (client, _, _) = makeClient()

        // Should not throw
        client.openStreaming(testDefaultTarget)
    }

    @Test
    fun `openStreaming requests credential for active targets`() = runTest {
        val authProvider = FakeAuthProvider()
        val (client, _, _) = makeClient(
            authProvider = authProvider,
            onStreamingTargetsChanged = { _ -> }
        )

        client.openStreaming(testDefaultTarget)

        assertTrue(authProvider.credentialCallCount > 0)
    }

    @Test
    fun `openStreaming invalidates credentials for a new target`() = runTest {
        val authProvider = FakeAuthProvider()
        val (client, _, _) = makeClient(
            authProvider = authProvider,
            onStreamingTargetsChanged = { _ -> }
        )

        client.openStreaming(testDefaultTarget)

        assertEquals(1, authProvider.invalidateCallCount)
    }

    @Test
    fun `openStreaming does not invalidate credentials for an already-registered target`() = runTest {
        val authProvider = FakeAuthProvider()
        val (client, _, _) = makeClient(
            authProvider = authProvider,
            onStreamingTargetsChanged = { _ -> }
        )

        client.openStreaming(testDefaultTarget)
        val countAfterFirst = authProvider.invalidateCallCount
        client.openStreaming(testDefaultTarget) // same target again

        assertEquals(countAfterFirst, authProvider.invalidateCallCount)
    }

    @Test
    fun `closeStreaming calls onStreamingEmpty when no active targets remain`() = runTest {
        var emptyCalled = false
        val (client, _, _) = makeClient(
            onStreamingEmpty = { emptyCalled = true }
        )

        client.openStreaming(testDefaultTarget)
        client.closeStreaming(testDefaultTarget)

        assertTrue(emptyCalled)
    }

    @Test
    fun `closeStreaming calls onStreamingTargetsChanged when other targets remain active`() = runTest {
        val target2 = EvaluationTarget("user-2", null, null)
        var changedCallCount = 0
        var emptyCalled = false
        val (client, _, _) = makeClient(
            onStreamingTargetsChanged = { _ -> changedCallCount++ },
            onStreamingEmpty = { emptyCalled = true },
        )

        client.openStreaming(testDefaultTarget)
        client.openStreaming(target2)
        client.closeStreaming(testDefaultTarget)

        // Called for open(default), open(target2), close(default) — target2 remains
        assertEquals(3, changedCallCount)
        assertFalse(emptyCalled)
    }
}
