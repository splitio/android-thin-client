package io.split.client.thin.internal.streaming

import io.split.android.client.service.sseclient.sseclient.EventSourceClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DefaultStreamingManagerTest {

    @Test
    fun `start opens connection`() = runBlocking {
        val eventSourceClient = FakeEventSourceClient()
        val manager = createManager(eventSourceClientProvider = { eventSourceClient })

        manager.start()
        delay(100)

        assertTrue(eventSourceClient.connectCalled)
    }

    @Test
    fun `stop closes connection`() = runBlocking {
        val eventSourceClient = FakeEventSourceClient()
        val manager = createManager(eventSourceClientProvider = { eventSourceClient })

        manager.start()
        delay(50)
        manager.stop()
        delay(50)

        assertTrue(eventSourceClient.connectCalled)
        // Verify it disconnected
        assertEquals(EventSourceClient.DISCONNECTED, eventSourceClient.status())
    }

    @Test
    fun `pause delegates to connection manager`() = runBlocking {
        var connectCount = 0
        val manager = createManager(eventSourceClientProvider = {
            connectCount++
            FakeEventSourceClient()
        })

        manager.start()
        delay(50)
        manager.pause()
        delay(200)

        assertEquals(1, connectCount)
    }

    @Test
    fun `resume reconnects after pause`() = runBlocking {
        var connectCount = 0
        val manager = createManager(eventSourceClientProvider = {
            connectCount++
            FakeEventSourceClient()
        })

        manager.start()
        delay(50)
        manager.pause()
        delay(50)
        manager.resume()
        delay(50)

        assertEquals(2, connectCount)
    }

    @Test
    fun `stopAll stops connection`() = runBlocking {
        val eventSourceClient = FakeEventSourceClient()
        val manager = createManager(eventSourceClientProvider = { eventSourceClient })

        manager.start()
        delay(50)
        manager.stopAll()
        delay(50)

        assertEquals(EventSourceClient.DISCONNECTED, eventSourceClient.status())
    }

    @Test
    fun `start after stopAll reconnects`() = runBlocking {
        var connectCount = 0
        val manager = createManager(eventSourceClientProvider = {
            connectCount++
            FakeEventSourceClient()
        })

        manager.start()
        delay(50)
        manager.stopAll()
        delay(50)
        manager.start()
        delay(50)

        assertEquals(2, connectCount)
    }

    @Test
    fun `tokenProvider is called on connection`() = runBlocking {
        var tokenRequests = 0
        val manager = createManager(tokenProvider = {
            tokenRequests++
            "test-token-$tokenRequests"
        })

        manager.start()
        delay(100)

        assertEquals(1, tokenRequests)
    }

    @Test
    fun `onEvaluationFetchNotification is forwarded from connection manager`() = runBlocking {
        val eventSourceClient = FakeEventSourceClient()
        var notificationCount = 0
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            onEvaluationFetchNotification = { notificationCount++ }
        )

        manager.start()
        delay(100)

        eventSourceClient.simulateMessage(
            mapOf(
                "channel" to "evaluations",
                "data" to """{"type":"EVALUATION_UPDATE","changeNumber":123}"""
            )
        )
        delay(100)

        assertEquals(1, notificationCount)
    }

    private fun createManager(
        tokenProvider: suspend () -> String = { "test-token" },
        eventSourceClientProvider: () -> FakeEventSourceClient = { FakeEventSourceClient() },
        onOccupancyZero: suspend () -> Unit = {},
        onEvaluationFetchNotification: suspend () -> Unit = {},
    ): DefaultStreamingManager {
        return DefaultStreamingManager(
            streamingUrl = "https://streaming.test.io/sse",
            tokenProvider = tokenProvider,
            eventSourceClientProvider = eventSourceClientProvider,
            backoffCounterFactory = { FakeBackoffCounter() },
            scope = CoroutineScope(Dispatchers.Unconfined),
            onOccupancyZero = onOccupancyZero,
            onEvaluationFetchNotification = onEvaluationFetchNotification,
        )
    }
}
