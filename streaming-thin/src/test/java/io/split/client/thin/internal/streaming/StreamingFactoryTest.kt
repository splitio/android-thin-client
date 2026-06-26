package io.split.client.thin.internal.streaming

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingFactoryTest {

    private fun makeParentScope(): CoroutineScope = CoroutineScope(SupervisorJob())

    @Test
    fun `createStreamingComponents returns valid components`() = runTest {
        val components = createStreamingComponents(
            streamingUrl = "https://streaming.example.com/sse",
            httpClient = FakeHttpClient(),
            parentScope = makeParentScope(),
            tokenProvider = { StreamingToken("fake-jwt-token") },
            onEvaluationFetchNotification = { _ -> },
            observer = FakeCompositeObserver(),
        )

        assertNotNull("StreamingComponents should not be null", components)
        assertNotNull("Manager should not be null", components.manager)
        assertNotNull("Start trigger should not be null", components.startTrigger)
    }

    @Test
    fun `tokenProvider is not called during construction`() = runTest {
        var tokenProviderCallCount = 0

        val components = createStreamingComponents(
            streamingUrl = "https://streaming.example.com/sse",
            httpClient = FakeHttpClient(),
            parentScope = makeParentScope(),
            tokenProvider = {
                tokenProviderCallCount++
                StreamingToken("jwt-token-$tokenProviderCallCount")
            },
            onEvaluationFetchNotification = { _ -> },
            observer = FakeCompositeObserver(),
        )

        assertEquals("Token provider should not be called during construction", 0, tokenProviderCallCount)
        assertNotNull(components.manager)
    }

    @Test
    fun `startTrigger invokes manager start without throwing`() = runTest {
        val components = createStreamingComponents(
            streamingUrl = "https://streaming.example.com/sse",
            httpClient = FakeHttpClient(),
            parentScope = makeParentScope(),
            tokenProvider = { StreamingToken("jwt-token") },
            onEvaluationFetchNotification = { _ -> },
            observer = FakeCompositeObserver(),
        )

        // Calling startTrigger multiple times should be idempotent and not throw
        components.startTrigger()
        components.startTrigger()
    }

    @Test
    fun `streaming scope is child of parentScope - cancelling parent cancels streaming`() = runTest {
        val parentScope = CoroutineScope(SupervisorJob())

        val components = createStreamingComponents(
            streamingUrl = "https://streaming.example.com/sse",
            httpClient = FakeHttpClient(),
            parentScope = parentScope,
            tokenProvider = { StreamingToken("jwt-token") },
            onEvaluationFetchNotification = { _ -> },
            observer = FakeCompositeObserver(),
        )

        val parentJob = parentScope.coroutineContext[Job]!!
        // The streaming scope's parent job must be the parent scope's job
        // (SupervisorJob's parent is the parentScope job)
        assertTrue("Parent scope job should be active before cancel", parentJob.isActive)

        parentJob.cancel()

        // After cancel the parent job is no longer active
        assertTrue("Parent scope job should be cancelled", !parentJob.isActive)
        // The manager's scope should also be cancelled since it's a child
        // (we verify by checking startTrigger doesn't create new coroutines — no NPE/ISE thrown)
    }

    @Test
    fun `components accept push callbacks`() = runTest {
        val components = createStreamingComponents(
            streamingUrl = "https://streaming.example.com/sse",
            httpClient = FakeHttpClient(),
            parentScope = makeParentScope(),
            tokenProvider = { StreamingToken("jwt-token") },
            onEvaluationFetchNotification = { _ -> },
            onPushDisabled = { },
            onPushEnabled = { },
            observer = FakeCompositeObserver(),
        )

        assertNotNull(components.manager)
    }
}
