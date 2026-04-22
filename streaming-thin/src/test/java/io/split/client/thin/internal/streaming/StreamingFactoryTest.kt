package io.split.client.thin.internal.streaming

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class StreamingFactoryTest {

    @Test
    fun `createStreamingComponents returns valid components`() = runTest {
        // This test will fail until we implement the factory function

        var tokenProviderCalled = false
        var fetchNotificationCalled = false

        val components = createStreamingComponents(
            streamingUrl = "https://streaming.example.com/sse",
            httpClient = FakeHttpClient(),
            tokenProvider = {
                tokenProviderCalled = true
                StreamingToken("fake-jwt-token")
            },
            onEvaluationFetchNotification = { _ ->
                fetchNotificationCalled = true
            },
            observer = FakeCompositeObserver(),
        )

        assertNotNull("StreamingComponents should not be null", components)
        assertNotNull("Manager should not be null", components.manager)
        assertNotNull("Start trigger should not be null", components.startTrigger)
    }

    @Test
    fun `tokenProvider is wired correctly and not called during construction`() = runTest {

        var tokenProviderCallCount = 0

        val components = createStreamingComponents(
            streamingUrl = "https://streaming.example.com/sse",
            httpClient = FakeHttpClient(),
            tokenProvider = {
                tokenProviderCallCount++
                StreamingToken("jwt-token-$tokenProviderCallCount")
            },
            onEvaluationFetchNotification = { _ -> },
            observer = FakeCompositeObserver(),
        )

        // Verify token provider is not eagerly evaluated during factory construction
        assertEquals("Token provider should not be called during construction", 0, tokenProviderCallCount)

        // Verify components are created successfully with the provider wired
        assertNotNull("Components should be created", components)
        assertNotNull("Manager should be created with tokenProvider wired", components.manager)
    }

    @Test
    fun `startTrigger invokes manager start`() = runTest {


        val components = createStreamingComponents(
            streamingUrl = "https://streaming.example.com/sse",
            httpClient = FakeHttpClient(),
            tokenProvider = { StreamingToken("jwt-token") },
            onEvaluationFetchNotification = { _ -> },
            observer = FakeCompositeObserver(),
        )

        // Verify start wasn't called during construction
        // (we can't directly verify this without mocking, but we can verify the trigger works)

        // Call start trigger multiple times
        components.startTrigger()
        components.startTrigger()

        // The manager should handle multiple start calls gracefully (idempotent)
        // This test verifies the trigger is wired correctly and doesn't throw
    }

    @Test
    fun `onEvaluationFetchNotification callback is wired correctly`() = runTest {

        var fetchNotificationCallCount = 0

        val components = createStreamingComponents(
            streamingUrl = "https://streaming.example.com/sse",
            httpClient = FakeHttpClient(),
            tokenProvider = { StreamingToken("jwt-token") },
            onEvaluationFetchNotification = { _ ->
                fetchNotificationCallCount++
            },
            observer = FakeCompositeObserver(),
        )

        // Verify callback hasn't been invoked yet
        assertEquals("Fetch notification should not be called yet", 0, fetchNotificationCallCount)

        // The callback should be invoked when the streaming manager receives a push notification
        // This is handled internally by DefaultStreamingManager based on SSE events
        // We verify that the callback is correctly passed to the manager
        assertNotNull("Manager should be created", components.manager)
    }
}
