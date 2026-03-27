package io.split.client.thin.internal.streaming

import io.split.android.client.service.sseclient.sseclient.EventSourceClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultStreamingManagerTest {

    @Test
    fun `start opens connection`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        val manager = createManager(eventSourceClientProvider = { eventSourceClient })

        manager.start()
        advanceUntilIdle()

        assertTrue(eventSourceClient.connectCalled)
    }

    @Test
    fun `stop closes connection`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        val manager = createManager(eventSourceClientProvider = { eventSourceClient })

        manager.start()
        advanceUntilIdle()
        manager.stop()
        advanceUntilIdle()

        assertTrue(eventSourceClient.connectCalled)
        assertEquals(EventSourceClient.DISCONNECTED, eventSourceClient.status())
    }

    @Test
    fun `pause delegates to connection manager`() = runTest {
        var connectCount = 0
        val manager = createManager(eventSourceClientProvider = {
            connectCount++
            FakeEventSourceClient()
        })

        manager.start()
        advanceUntilIdle()
        manager.pause()
        advanceUntilIdle()

        assertEquals(1, connectCount)
    }

    @Test
    fun `resume reconnects after pause`() = runTest {
        var connectCount = 0
        val manager = createManager(eventSourceClientProvider = {
            connectCount++
            FakeEventSourceClient()
        })

        manager.start()
        advanceUntilIdle()
        manager.pause()
        advanceUntilIdle()
        manager.resume()
        advanceUntilIdle()

        assertEquals(2, connectCount)
    }

    @Test
    fun `stopAll stops connection`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        val manager = createManager(eventSourceClientProvider = { eventSourceClient })

        manager.start()
        advanceUntilIdle()
        manager.stopAll()
        advanceUntilIdle()

        assertEquals(EventSourceClient.DISCONNECTED, eventSourceClient.status())
    }

    @Test
    fun `start after stopAll reconnects`() = runTest {
        var connectCount = 0
        val manager = createManager(eventSourceClientProvider = {
            connectCount++
            FakeEventSourceClient()
        })

        manager.start()
        advanceUntilIdle()
        manager.stopAll()
        advanceUntilIdle()
        manager.start()
        advanceUntilIdle()

        assertEquals(2, connectCount)
    }

    @Test
    fun `tokenProvider is called on connection`() = runTest {
        var tokenRequests = 0
        val manager = createManager(tokenProvider = {
            tokenRequests++
            "test-token-$tokenRequests"
        })

        manager.start()
        advanceUntilIdle()

        assertEquals(1, tokenRequests)
    }

    @Test
    fun `onEvaluationFetchNotification is forwarded from connection manager`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        var notificationCount = 0
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            onEvaluationFetchNotification = { notificationCount++ }
        )

        manager.start()
        advanceUntilIdle()

        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"evaluations","data":"{\"type\":\"EVALUATION_UPDATE\",\"changeNumber\":123}","timestamp":1000}""")
        )
        advanceUntilIdle()

        assertEquals(1, notificationCount)
    }

    private fun TestScope.createManager(
        tokenProvider: suspend () -> String = { "test-token" },
        eventSourceClientProvider: () -> FakeEventSourceClient = { FakeEventSourceClient() },
        onOccupancyZero: suspend () -> Unit = {},
        onEvaluationFetchNotification: suspend () -> Unit = {},
    ): DefaultStreamingManager = DefaultStreamingManager(
        streamingUrl = "https://streaming.test.io/sse",
        tokenProvider = tokenProvider,
        eventSourceClientProvider = eventSourceClientProvider,
        backoffCounterFactory = { FakeBackoffCounter() },
        scope = this,
        connectionDispatcher = UnconfinedTestDispatcher(testScheduler),
        onOccupancyZero = onOccupancyZero,
        onEvaluationFetchNotification = onEvaluationFetchNotification,
    )
}
