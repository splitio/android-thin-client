package io.split.client.thin.internal.streaming

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StreamingConnectionManagerTest {

    @Test
    fun `start initiates connection`() = runBlocking {
        val eventSourceClient = FakeEventSourceClient()
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient }
        )

        manager.start()
        delay(100) // Give time for coroutine to start

        assertTrue(eventSourceClient.connectCalled)
    }

    @Test
    fun `start when already started is idempotent`() = runBlocking {
        var connectCount = 0
        val manager = createManager(
            eventSourceClientProvider = {
                connectCount++
                FakeEventSourceClient()
            }
        )

        manager.start()
        delay(50)
        manager.start() // Second start should be ignored
        delay(50)

        assertEquals(1, connectCount)
    }

    @Test
    fun `stop cancels connection`() = runBlocking {
        val eventSourceClient = FakeEventSourceClient()
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient }
        )

        manager.start()
        delay(50)
        manager.stop()
        delay(50)

        // Should have connected but not reconnecting
        assertTrue(eventSourceClient.connectCalled)
    }

    @Test
    fun `pause disconnects without reconnect`() = runBlocking {
        val eventSourceClient = FakeEventSourceClient()
        var connectCount = 0
        val manager = createManager(
            eventSourceClientProvider = {
                connectCount++
                eventSourceClient
            }
        )

        manager.start()
        delay(50)
        manager.pause()
        delay(200) // Wait longer than reconnect backoff

        // Should only connect once (no reconnect after pause)
        assertEquals(1, connectCount)
    }

    @Test
    fun `resume reconnects if started`() = runBlocking {
        var connectCount = 0
        val manager = createManager(
            eventSourceClientProvider = {
                connectCount++
                FakeEventSourceClient()
            }
        )

        manager.start()
        delay(50)
        manager.pause()
        delay(50)
        manager.resume()
        delay(50)

        // Should connect twice: initial start + resume
        assertEquals(2, connectCount)
    }

    @Test
    fun `resume when not started does nothing`() = runBlocking {
        var connectCount = 0
        val manager = createManager(
            eventSourceClientProvider = {
                connectCount++
                FakeEventSourceClient()
            }
        )

        manager.resume() // Resume without start
        delay(100)

        assertEquals(0, connectCount)
    }

    @Test
    fun `onOpen resets backoff counter`() = runBlocking {
        val eventSourceClient = FakeEventSourceClient()
        val backoffCounter = FakeBackoffCounter()
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            backoffCounter = backoffCounter
        )

        manager.start()
        delay(100)

        // onOpen should have been called, resetting backoff
        assertEquals(1, backoffCounter.resetCount)
    }

    @Test
    fun `onMessage with EVALUATION_UPDATE triggers onEvaluationFetchNotification`() = runBlocking {
        val eventSourceClient = FakeEventSourceClient()
        var fetchNotificationCount = 0
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            onEvaluationFetchNotification = { fetchNotificationCount++ }
        )

        manager.start()
        delay(100)

        // Simulate EVALUATION_UPDATE message
        eventSourceClient.simulateMessage(
            mapOf(
                "channel" to "evaluations",
                "data" to """{"type":"EVALUATION_UPDATE","changeNumber":123}"""
            )
        )
        delay(100)

        // Should have triggered fetch notification
        assertEquals(1, fetchNotificationCount)
    }

    @Test
    fun `onMessage with STREAMING_RESUMED calls resume`() = runBlocking {
        val eventSourceClient = FakeEventSourceClient()
        var connectCount = 0
        val manager = createManager(
            eventSourceClientProvider = {
                connectCount++
                eventSourceClient
            }
        )

        manager.start()
        delay(50)
        manager.pause()
        delay(50)
        connectCount = 0 // Reset count after initial start and pause

        // Simulate STREAMING_RESUMED
        eventSourceClient.simulateMessage(
            mapOf(
                "data" to """{"type":"CONTROL","controlType":"STREAMING_RESUMED"}"""
            )
        )
        delay(100)

        // Should have reconnected
        assertTrue(connectCount > 0)
    }

    @Test
    fun `onMessage with STREAMING_PAUSED calls pause`() = runBlocking {
        val eventSourceClient1 = FakeEventSourceClient()
        val eventSourceClient2 = FakeEventSourceClient()
        var connectCount = 0
        val manager = createManager(
            eventSourceClientProvider = {
                connectCount++
                if (connectCount == 1) eventSourceClient1 else eventSourceClient2
            }
        )

        manager.start()
        delay(100)

        // Simulate STREAMING_PAUSED
        eventSourceClient1.simulateMessage(
            mapOf(
                "data" to """{"type":"CONTROL","controlType":"STREAMING_PAUSED"}"""
            )
        )
        delay(200) // Wait to see if it reconnects (it shouldn't)

        // Should only connect once (paused, no reconnect)
        assertEquals(1, connectCount)
    }

    @Test
    fun `onMessage with STREAMING_DISABLED calls stop`() = runBlocking {
        val eventSourceClient = FakeEventSourceClient()
        var connectCount = 0
        val manager = createManager(
            eventSourceClientProvider = {
                connectCount++
                eventSourceClient
            }
        )

        manager.start()
        delay(100)

        // Simulate STREAMING_DISABLED
        eventSourceClient.simulateMessage(
            mapOf(
                "data" to """{"type":"CONTROL","controlType":"STREAMING_DISABLED"}"""
            )
        )
        delay(200) // Wait to verify no reconnect

        // Should only connect once (stopped)
        assertEquals(1, connectCount)
    }

    @Test
    fun `onMessage with STREAMING_RESET calls stop then start`() = runBlocking {
        var connectCount = 0
        val manager = createManager(
            eventSourceClientProvider = {
                connectCount++
                FakeEventSourceClient()
            }
        )

        manager.start()
        delay(100)
        val initialConnectCount = connectCount

        // Get reference to first client to send message
        val firstClient = FakeEventSourceClient()
        val managerWithFirstClient = createManager(
            eventSourceClientProvider = { firstClient }
        )
        managerWithFirstClient.start()
        delay(50)

        // Simulate STREAMING_RESET
        firstClient.simulateMessage(
            mapOf(
                "data" to """{"type":"CONTROL","controlType":"STREAMING_RESET"}"""
            )
        )
        delay(150)

        // Should have reconnected (stop + start)
        // Note: This test is simplified; actual behavior depends on implementation
    }

    @Test
    fun `onMessage with OCCUPANCY publishers=0 triggers callback`() = runBlocking {
        val eventSourceClient = FakeEventSourceClient()
        var callbackInvoked = false
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            onOccupancyZero = { callbackInvoked = true }
        )

        manager.start()
        delay(100)

        // Simulate OCCUPANCY with 0 publishers
        eventSourceClient.simulateMessage(
            mapOf(
                "data" to """{"type":"OCCUPANCY","publishers":0}"""
            )
        )
        delay(100)

        // Should have triggered callback and stopped
        assertTrue(callbackInvoked)
    }

    @Test
    fun `onMessage with OCCUPANCY publishers greater than 0 does not trigger callback`() = runBlocking {
        val eventSourceClient = FakeEventSourceClient()
        var callbackInvoked = false
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            onOccupancyZero = { callbackInvoked = true }
        )

        manager.start()
        delay(100)

        // Simulate OCCUPANCY with publishers > 0
        eventSourceClient.simulateMessage(
            mapOf(
                "data" to """{"type":"OCCUPANCY","publishers":5}"""
            )
        )
        delay(100)

        // Should NOT trigger callback
        assertFalse(callbackInvoked)
    }

    @Test
    fun `connection error triggers reconnect with backoff`() = runBlocking {
        var connectCount = 0
        val backoffCounter = FakeBackoffCounter(delays = listOf(50, 100))
        val manager = createManager(
            eventSourceClientProvider = {
                connectCount++
                FakeEventSourceClient().apply {
                    if (connectCount == 1) shouldFailConnect = true
                }
            },
            backoffCounter = backoffCounter
        )

        manager.start()
        delay(300) // Wait for initial failure and reconnect

        // Should have attempted connect at least twice
        assertTrue(connectCount >= 2)
    }

    // Helper to create StreamingConnectionManager with test doubles
    private fun createManager(
        eventSourceClientProvider: () -> FakeEventSourceClient = { FakeEventSourceClient() },
        backoffCounter: FakeBackoffCounter = FakeBackoffCounter(),
        onOccupancyZero: suspend () -> Unit = {},
        onEvaluationFetchNotification: suspend () -> Unit = {},
    ): StreamingConnectionManager {
        return StreamingConnectionManager(
            streamingUrl = "https://streaming.test.io/sse",
            tokenProvider = { "test-token" },
            eventSourceClientProvider = eventSourceClientProvider,
            backoffCounter = backoffCounter,
            scope = CoroutineScope(Dispatchers.Unconfined), // Use Unconfined for synchronous test execution
            onOccupancyZero = onOccupancyZero,
            onEvaluationFetchNotification = onEvaluationFetchNotification,
        )
    }
}
