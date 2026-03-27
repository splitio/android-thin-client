package io.split.client.thin.internal.streaming

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamingConnectionManagerTest {

    @Test
    fun `start initiates connection`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        val manager = createManager(eventSourceClientProvider = { eventSourceClient })

        manager.start()
        advanceUntilIdle()

        assertTrue(eventSourceClient.connectCalled)
    }

    @Test
    fun `start when already started is idempotent`() = runTest {
        var connectCount = 0
        val manager = createManager(
            eventSourceClientProvider = {
                connectCount++
                FakeEventSourceClient()
            }
        )

        manager.start()
        advanceUntilIdle()
        manager.start()
        advanceUntilIdle()

        assertEquals(1, connectCount)
    }

    @Test
    fun `stop cancels connection`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        val manager = createManager(eventSourceClientProvider = { eventSourceClient })

        manager.start()
        advanceUntilIdle()
        manager.stop()
        advanceUntilIdle()

        assertTrue(eventSourceClient.connectCalled)
    }

    @Test
    fun `pause disconnects without reconnect`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        var connectCount = 0
        val manager = createManager(
            eventSourceClientProvider = {
                connectCount++
                eventSourceClient
            }
        )

        manager.start()
        advanceUntilIdle()
        manager.pause()
        advanceUntilIdle()

        // Should only connect once (no reconnect after pause)
        assertEquals(1, connectCount)
    }

    @Test
    fun `resume reconnects if paused`() = runTest {
        var connectCount = 0
        val manager = createManager(
            eventSourceClientProvider = {
                connectCount++
                FakeEventSourceClient()
            }
        )

        manager.start()
        advanceUntilIdle()
        manager.pause()
        advanceUntilIdle()
        manager.resume()
        advanceUntilIdle()

        // Should connect twice: initial start + resume
        assertEquals(2, connectCount)
    }

    @Test
    fun `resume when not started does nothing`() = runTest {
        var connectCount = 0
        val manager = createManager(
            eventSourceClientProvider = {
                connectCount++
                FakeEventSourceClient()
            }
        )

        manager.resume()
        advanceUntilIdle()

        assertEquals(0, connectCount)
    }

    @Test
    fun `onOpen resets backoff counter`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        val backoffCounter = FakeBackoffCounter()
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            backoffCounter = backoffCounter,
        )

        manager.start()
        advanceUntilIdle()

        assertEquals(1, backoffCounter.resetCount)
    }

    @Test
    fun `onMessage with EVALUATION_UPDATE triggers onEvaluationFetchNotification`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        var fetchNotificationCount = 0
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            onEvaluationFetchNotification = { fetchNotificationCount++ },
        )

        manager.start()
        advanceUntilIdle()

        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"evaluations","data":"{\"type\":\"EVALUATION_UPDATE\",\"changeNumber\":123}","timestamp":1000}""")
        )
        advanceUntilIdle()

        assertEquals(1, fetchNotificationCount)
    }

    @Test
    fun `onMessage with STREAMING_RESUMED triggers reconnect when paused`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        var connectCount = 0
        val manager = createManager(
            eventSourceClientProvider = {
                connectCount++
                eventSourceClient
            }
        )

        manager.start()
        advanceUntilIdle()
        manager.pause()
        advanceUntilIdle()
        connectCount = 0

        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"control_pri","data":"{\"type\":\"CONTROL\",\"controlType\":\"STREAMING_RESUMED\"}","timestamp":1000}""")
        )
        advanceUntilIdle()

        assertTrue(connectCount > 0)
    }

    @Test
    fun `onMessage with STREAMING_PAUSED does not reconnect`() = runTest {
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
        advanceUntilIdle()

        eventSourceClient1.simulateMessage(
            mapOf("data" to """{"channel":"control_pri","data":"{\"type\":\"CONTROL\",\"controlType\":\"STREAMING_PAUSED\"}","timestamp":1000}""")
        )
        advanceUntilIdle()

        // Should only connect once (paused, no reconnect)
        assertEquals(1, connectCount)
    }

    @Test
    fun `onMessage with STREAMING_DISABLED stops without reconnect`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        var connectCount = 0
        val manager = createManager(
            eventSourceClientProvider = {
                connectCount++
                eventSourceClient
            }
        )

        manager.start()
        advanceUntilIdle()

        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"control_pri","data":"{\"type\":\"CONTROL\",\"controlType\":\"STREAMING_DISABLED\"}","timestamp":1000}""")
        )
        advanceUntilIdle()

        assertEquals(1, connectCount)
    }

    @Test
    fun `onMessage with STREAMING_RESET reconnects`() = runTest {
        val firstClient = FakeEventSourceClient()
        var connectCount = 0
        val manager = createManager(
            eventSourceClientProvider = {
                connectCount++
                firstClient
            }
        )

        manager.start()
        advanceUntilIdle()
        assertEquals(1, connectCount)

        firstClient.simulateMessage(
            mapOf("data" to """{"channel":"control_pri","data":"{\"type\":\"CONTROL\",\"controlType\":\"STREAMING_RESET\"}","timestamp":1000}""")
        )
        advanceUntilIdle()

        // stop + start should have caused a second connect
        assertTrue(connectCount >= 2)
    }

    @Test
    fun `onMessage with OCCUPANCY publishers=0 triggers callback`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        var callbackInvoked = false
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            onOccupancyZero = { callbackInvoked = true },
        )

        manager.start()
        advanceUntilIdle()

        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"[?occupancy=metrics.publishers]control_pri","data":"{\"metrics\":{\"publishers\":0}}","timestamp":1000}""")
        )
        advanceUntilIdle()

        assertTrue(callbackInvoked)
    }

    @Test
    fun `onMessage with OCCUPANCY publishers greater than 0 does not trigger callback`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        var callbackInvoked = false
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            onOccupancyZero = { callbackInvoked = true },
        )

        manager.start()
        advanceUntilIdle()

        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"[?occupancy=metrics.publishers]control_pri","data":"{\"metrics\":{\"publishers\":5}}","timestamp":1000}""")
        )
        advanceUntilIdle()

        assertFalse(callbackInvoked)
    }

    @Test
    fun `connection error triggers reconnect with backoff`() = runTest {
        var connectCount = 0
        val backoffCounter = FakeBackoffCounter(delays = listOf(50, 100))
        val manager = createManager(
            eventSourceClientProvider = {
                connectCount++
                FakeEventSourceClient().apply {
                    if (connectCount == 1) shouldFailConnect = true
                }
            },
            backoffCounter = backoffCounter,
        )

        manager.start()
        advanceUntilIdle()

        assertTrue(connectCount >= 2)
    }

    @Test
    fun `connect builds URL with v=1_1, accessToken, and channel params`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            channelExtractor = { listOf("evaluations_abc", "[?occupancy=metrics.publishers]control_pri") },
        )

        manager.start()
        advanceUntilIdle()

        val uri = eventSourceClient.lastUri.toString()
        assertTrue("URL should contain v=1.1", uri.contains("v=1.1"))
        assertTrue("URL should contain accessToken", uri.contains("accessToken=test-token"))
        assertTrue("URL should contain channel", uri.contains("channel="))
        assertTrue("URL should contain evaluations_abc channel", uri.contains("evaluations_abc"))
        assertFalse("URL should not use ?token= param", uri.contains("?token="))
    }

    private fun TestScope.createManager(
        eventSourceClientProvider: () -> FakeEventSourceClient = { FakeEventSourceClient() },
        backoffCounter: FakeBackoffCounter = FakeBackoffCounter(),
        onOccupancyZero: suspend () -> Unit = {},
        onEvaluationFetchNotification: suspend () -> Unit = {},
        channelExtractor: (String) -> List<String> = { listOf("evaluations", "[?occupancy=metrics.publishers]control_pri") },
    ): StreamingConnectionManager = StreamingConnectionManager(
        streamingUrl = "https://streaming.test.io/sse",
        tokenProvider = { "test-token" },
        channelExtractor = channelExtractor,
        eventSourceClientProvider = eventSourceClientProvider,
        backoffCounter = backoffCounter,
        scope = this,
        connectionDispatcher = UnconfinedTestDispatcher(testScheduler),
        onOccupancyZero = onOccupancyZero,
        onEvaluationFetchNotification = onEvaluationFetchNotification,
    )
}
