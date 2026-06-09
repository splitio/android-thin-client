package io.split.client.thin.internal.streaming

import io.split.client.thin.internal.observer.CompositeObserver
import io.split.client.thin.internal.observer.ObservableEventType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

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
    fun `resume when already started does not reconnect`() = runTest {
        var connectCount = 0
        val manager = createManager(
            eventSourceClientProvider = {
                connectCount++
                FakeEventSourceClient()
            }
        )

        manager.start()
        advanceUntilIdle()
        assertEquals(1, connectCount)

        // resume() while already Started should be a no-op
        manager.resume()
        advanceUntilIdle()
        assertEquals(1, connectCount)
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
            onEvaluationFetchNotification = { _ -> fetchNotificationCount++ },
        )

        manager.start()
        advanceUntilIdle()

        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"evaluations","data":"{\"type\":\"EVALUATIONS_UPDATE\",\"changeNumber\":123}","timestamp":1000}""")
        )
        advanceUntilIdle()

        // 1 from onOpen (catch-up fetch) + 1 from the push notification
        assertEquals(2, fetchNotificationCount)
    }

    @Test
    fun `onOpen passes null notification to onEvaluationFetchNotification`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        var capturedNotification: EvaluationUpdateNotification? = EvaluationUpdateNotification(0L, null, 0L) // non-null sentinel
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            onEvaluationFetchNotification = { n -> capturedNotification = n },
        )

        manager.start()
        advanceUntilIdle()

        assertNull(capturedNotification)
    }

    @Test
    fun `onMessage EVALUATION_UPDATE passes notification to onEvaluationFetchNotification`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        var capturedNotification: EvaluationUpdateNotification? = null
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            onEvaluationFetchNotification = { n -> capturedNotification = n },
        )

        manager.start()
        advanceUntilIdle()
        // Reset after onOpen's null call
        capturedNotification = null

        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"evaluations","data":"{\"type\":\"EVALUATIONS_UPDATE\",\"changeNumber\":42,\"i\":60000,\"s\":10,\"h\":1}","timestamp":1000}""")
        )
        advanceUntilIdle()

        assertNotNull(capturedNotification)
        assertEquals(42L, capturedNotification!!.changeNumber)
        assertEquals(60000L, capturedNotification!!.updateIntervalMs)
        assertEquals(10, capturedNotification!!.algorithmSeed)
        assertEquals(1, capturedNotification!!.hashingAlgorithm)
    }

    @Test
    fun `STREAMING_PAUSED keeps socket open and fires onPushDisabled`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        var connectCount = 0
        var pushDisabledCount = 0
        val manager = createManager(
            eventSourceClientProvider = {
                connectCount++
                eventSourceClient
            },
            onPushDisabled = { pushDisabledCount++ },
        )

        manager.start()
        advanceUntilIdle()
        eventSourceClient.disconnectCalled = false

        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"control_pri","data":"{\"type\":\"CONTROL\",\"controlType\":\"STREAMING_PAUSED\"}","timestamp":1000}""")
        )
        advanceUntilIdle()

        assertEquals("must not reconnect on pause", 1, connectCount)
        assertFalse("must keep the socket open on control pause", eventSourceClient.disconnectCalled)
        assertEquals(1, pushDisabledCount)
    }

    @Test
    fun `STREAMING_RESUMED fires onPushEnabled over the same socket`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        var connectCount = 0
        var pushEnabledCount = 0
        val manager = createManager(
            eventSourceClientProvider = {
                connectCount++
                eventSourceClient
            },
            onPushEnabled = { pushEnabledCount++ },
        )

        manager.start()
        advanceUntilIdle()

        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"control_pri","data":"{\"type\":\"CONTROL\",\"controlType\":\"STREAMING_PAUSED\"}","timestamp":1000}""")
        )
        advanceUntilIdle()
        eventSourceClient.disconnectCalled = false

        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"control_pri","data":"{\"type\":\"CONTROL\",\"controlType\":\"STREAMING_RESUMED\"}","timestamp":2000}""")
        )
        advanceUntilIdle()

        assertEquals("must not reconnect on resume", 1, connectCount)
        assertFalse("must reuse the live socket on resume", eventSourceClient.disconnectCalled)
        assertEquals(1, pushEnabledCount)
    }

    @Test
    fun `stale control notification with older timestamp is ignored`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        var pushDisabledCount = 0
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            onPushDisabled = { pushDisabledCount++ },
        )

        manager.start()
        advanceUntilIdle()

        // Newest pause first
        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"control_pri","data":"{\"type\":\"CONTROL\",\"controlType\":\"STREAMING_RESUMED\"}","timestamp":2000}""")
        )
        advanceUntilIdle()
        // Stale paused (older timestamp) must be ignored
        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"control_pri","data":"{\"type\":\"CONTROL\",\"controlType\":\"STREAMING_PAUSED\"}","timestamp":1000}""")
        )
        advanceUntilIdle()

        assertEquals("stale pause must not fire onPushDisabled", 0, pushDisabledCount)
    }

    @Test
    fun `second STREAMING_PAUSED while already paused does not re-fire onPushDisabled`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        var pushDisabledCount = 0
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            onPushDisabled = { pushDisabledCount++ },
        )

        manager.start()
        advanceUntilIdle()

        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"control_pri","data":"{\"type\":\"CONTROL\",\"controlType\":\"STREAMING_PAUSED\"}","timestamp":1000}""")
        )
        advanceUntilIdle()
        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"control_pri","data":"{\"type\":\"CONTROL\",\"controlType\":\"STREAMING_PAUSED\"}","timestamp":2000}""")
        )
        advanceUntilIdle()

        assertEquals(1, pushDisabledCount)
    }

    @Test
    fun `EvaluationUpdateNotification is ignored while push is down`() = runTest {
        val observer = FakeCompositeObserver()
        val eventSourceClient = FakeEventSourceClient()
        var fetchCount = 0
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            observer = observer,
            onEvaluationFetchNotification = { _ -> fetchCount++ },
        )

        manager.start()
        advanceUntilIdle()
        // onOpen catch-up fetch happened (1)
        assertEquals(1, fetchCount)

        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"control_pri","data":"{\"type\":\"CONTROL\",\"controlType\":\"STREAMING_PAUSED\"}","timestamp":1000}""")
        )
        advanceUntilIdle()
        observer.events.clear()

        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"evaluations","data":"{\"type\":\"EVALUATIONS_UPDATE\",\"changeNumber\":42}","timestamp":2000}""")
        )
        advanceUntilIdle()

        assertEquals("eval fetch must be skipped while push is down", 1, fetchCount)
        assertFalse(
            "no notification-received event while push is down",
            observer.events.any { it.type == ObservableEventType.STREAMING_NOTIFICATION_RECEIVED }
        )
    }

    @Test
    fun `unbounded eval notification coalesces into in-flight fetch and bumps target change number`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        val holder = java.util.concurrent.atomic.AtomicLong(Long.MIN_VALUE)
        val gate = CompletableDeferred<Unit>()
        var fetchInvocations = 0
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            evalChangeNumberHolder = holder,
            onEvaluationFetchNotification = { n ->
                if (n != null) {
                    fetchInvocations++
                    gate.await() // park, simulating the jitter delay
                }
            },
        )

        manager.start()
        advanceUntilIdle()

        // First notification launches the fetch, which parks at the gate.
        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"evaluations","data":"{\"type\":\"EVALUATIONS_UPDATE\",\"changeNumber\":10,\"u\":0}","timestamp":1000}""")
        )
        advanceUntilIdle()
        assertEquals(1, fetchInvocations)

        // Second notification arrives while the first is parked: it must coalesce (no new fetch)
        // and only advance the shared target change number.
        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"evaluations","data":"{\"type\":\"EVALUATIONS_UPDATE\",\"changeNumber\":20,\"u\":0}","timestamp":2000}""")
        )
        advanceUntilIdle()

        assertEquals("second notification must not start a new fetch", 1, fetchInvocations)
        assertEquals("target change number must reflect the latest notification", 20L, holder.get())

        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `bounded eval notification cancels and replaces the in-flight fetch`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        val holder = java.util.concurrent.atomic.AtomicLong(Long.MIN_VALUE)
        val gate = CompletableDeferred<Unit>()
        var fetchInvocations = 0
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            evalChangeNumberHolder = holder,
            onEvaluationFetchNotification = { n ->
                if (n != null) {
                    fetchInvocations++
                    gate.await()
                }
            },
        )

        manager.start()
        advanceUntilIdle()

        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"evaluations","data":"{\"type\":\"EVALUATIONS_UPDATE\",\"changeNumber\":10,\"u\":0}","timestamp":1000}""")
        )
        advanceUntilIdle()
        assertEquals(1, fetchInvocations)

        // A bounded notification carries a key bitmap, so it must NOT coalesce — it cancels and
        // replaces the in-flight fetch, starting a fresh one.
        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"evaluations","data":"{\"type\":\"EVALUATIONS_UPDATE\",\"changeNumber\":20,\"u\":1,\"d\":\"payload\"}","timestamp":2000}""")
        )
        advanceUntilIdle()

        assertEquals("bounded notification must start a new fetch", 2, fetchInvocations)
        assertEquals(20L, holder.get())

        gate.complete(Unit)
        advanceUntilIdle()
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
    fun `OCCUPANCY zero keeps socket open and fires onPushDisabled`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        var pushDisabledCount = 0
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            onPushDisabled = { pushDisabledCount++ },
        )

        manager.start()
        advanceUntilIdle()
        eventSourceClient.disconnectCalled = false

        // publishers drop to 0
        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"[?occupancy=metrics.publishers]control_pri","data":"{\"metrics\":{\"publishers\":0}}","timestamp":2000}""")
        )
        advanceUntilIdle()

        assertEquals(1, pushDisabledCount)
        assertFalse("occupancy zero must keep the socket open", eventSourceClient.disconnectCalled)
    }

    @Test
    fun `OCCUPANCY recovery fires onPushEnabled when not control-paused`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        var pushDisabledCount = 0
        var pushEnabledCount = 0
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            onPushDisabled = { pushDisabledCount++ },
            onPushEnabled = { pushEnabledCount++ },
        )

        manager.start()
        advanceUntilIdle()

        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"[?occupancy=metrics.publishers]control_pri","data":"{\"metrics\":{\"publishers\":0}}","timestamp":1000}""")
        )
        advanceUntilIdle()
        assertEquals(1, pushDisabledCount)

        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"[?occupancy=metrics.publishers]control_pri","data":"{\"metrics\":{\"publishers\":2}}","timestamp":2000}""")
        )
        advanceUntilIdle()

        assertEquals(1, pushEnabledCount)
    }

    @Test
    fun `combined gate - occupancy recovery while control-paused stays down`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        var pushEnabledCount = 0
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            onPushEnabled = { pushEnabledCount++ },
        )

        manager.start()
        advanceUntilIdle()

        // Control pause brings push down.
        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"control_pri","data":"{\"type\":\"CONTROL\",\"controlType\":\"STREAMING_PAUSED\"}","timestamp":1000}""")
        )
        advanceUntilIdle()
        // Occupancy >0 alone must not re-enable push while still control-paused.
        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"[?occupancy=metrics.publishers]control_pri","data":"{\"metrics\":{\"publishers\":5}}","timestamp":2000}""")
        )
        advanceUntilIdle()

        assertEquals(0, pushEnabledCount)
    }

    @Test
    fun `OCCUPANCY publishers greater than zero does not fire onPushDisabled`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        var pushDisabledCount = 0
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            onPushDisabled = { pushDisabledCount++ },
        )

        manager.start()
        advanceUntilIdle()

        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"[?occupancy=metrics.publishers]control_pri","data":"{\"metrics\":{\"publishers\":5}}","timestamp":1000}""")
        )
        advanceUntilIdle()

        assertEquals(0, pushDisabledCount)
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
    fun `connection error waits backoff seconds converted to milliseconds before reconnecting`() = runTest {
        var connectCount = 0
        val backoffCounter = FakeBackoffCounter(delays = listOf(1)) // counter returns 1 (second)
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
        advanceTimeBy(999) // just under 1 second — reconnect must NOT have happened yet
        assertEquals("reconnect should not happen before 1000ms", 1, connectCount)

        advanceTimeBy(2)   // now past 1000ms — reconnect should happen
        advanceUntilIdle()
        assertEquals("reconnect should happen after 1000ms", 2, connectCount)
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

    @Test
    fun `connecting notifies streaming_connect_started and streaming_connected events`() = runTest {
        val observer = FakeCompositeObserver()
        val manager = createManager(observer = observer)

        manager.start()
        advanceUntilIdle()

        val types = observer.events.map { it.type }
        assertTrue(types.contains(ObservableEventType.STREAMING_CONNECT_STARTED))
        assertTrue(types.contains(ObservableEventType.STREAMING_CONNECTED))
    }

    @Test
    fun `receiving a notification notifies streaming_notification_received with notificationType`() = runTest {
        val observer = FakeCompositeObserver()
        val eventSourceClient = FakeEventSourceClient()
        val manager = createManager(eventSourceClientProvider = { eventSourceClient }, observer = observer)

        manager.start()
        advanceUntilIdle()

        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"evaluations","data":"{\"type\":\"EVALUATIONS_UPDATE\",\"changeNumber\":1}","timestamp":1000}""")
        )
        advanceUntilIdle()

        val notifEvent = observer.events.find { it.type == ObservableEventType.STREAMING_NOTIFICATION_RECEIVED }
        assertNotNull(notifEvent)
        assertEquals("EVALUATIONS_UPDATE", notifEvent!!.properties["notificationType"])
    }

    @Test
    fun `disconnecting notifies streaming_disconnected event`() = runTest {
        val observer = FakeCompositeObserver()
        val manager = createManager(observer = observer)

        manager.start()
        advanceUntilIdle()
        observer.events.clear()

        manager.stop()
        advanceUntilIdle()

        assertTrue(observer.events.any { it.type == ObservableEventType.STREAMING_DISCONNECTED })
    }

    @Test
    fun `pause cancels in-flight evaluation fetch notification callback`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        var fetchCallbackInvoked = false
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            onEvaluationFetchNotification = { _ ->
                fetchCallbackInvoked = true
            }
        )

        manager.start()
        advanceUntilIdle()

        // Trigger PUSH notification
        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"evaluations","data":"{\"type\":\"EVALUATIONS_UPDATE\",\"changeNumber":123,\"i\":5000,\"s\":10,\"h":1}","timestamp":1000}""")
        )
        advanceUntilIdle() // Let callback complete

        assertTrue("Callback should have been invoked", fetchCallbackInvoked)
        fetchCallbackInvoked = false // Reset

        // Trigger another PUSH notification, then pause immediately
        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"evaluations","data":"{\"type\":\"EVALUATIONS_UPDATE\",\"changeNumber":124,\"i\":5000,\"s\":10,\"h\":1}","timestamp":2000}""")
        )
        manager.pause()
        advanceUntilIdle()

        // Second callback should have been cancelled and NOT invoked
        assertFalse("Second callback should not have been invoked after pause", fetchCallbackInvoked)
    }

    @Test
    fun `connection that opens after stop does not emit connected, fetch, and is disconnected`() = runTest {
        val observer = FakeCompositeObserver()
        // Defer onOpen so we can simulate the blocking connect completing AFTER stop().
        val eventSourceClient = FakeEventSourceClient().apply { shouldFailOnOpen = true }
        var fetchInvoked = false
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            observer = observer,
            onEvaluationFetchNotification = { fetchInvoked = true },
        )

        manager.start()
        advanceUntilIdle()
        manager.stop()
        advanceUntilIdle()
        observer.events.clear()
        eventSourceClient.disconnectCalled = false

        // The in-flight (non-cancellable) connect finally opens after stop ran.
        eventSourceClient.lastHandler?.onOpen()
        advanceUntilIdle()

        assertFalse(
            "must not emit STREAMING_CONNECTED after stop",
            observer.events.any { it.type == ObservableEventType.STREAMING_CONNECTED }
        )
        assertFalse("must not trigger a fetch after stop", fetchInvoked)
        assertTrue("the leaked connection must be torn down", eventSourceClient.disconnectCalled)
    }

    @Test
    fun `message delivered after stop is ignored`() = runTest {
        val observer = FakeCompositeObserver()
        val eventSourceClient = FakeEventSourceClient()
        var fetchInvoked = false
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            observer = observer,
            onEvaluationFetchNotification = { fetchInvoked = true },
        )

        manager.start()
        advanceUntilIdle()
        manager.stop()
        advanceUntilIdle()
        observer.events.clear()
        fetchInvoked = false

        // A delayed SSE event arrives after stop() ran but before the read loop noticed closure.
        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"evaluations","data":"{\"type\":\"EVALUATIONS_UPDATE\",\"changeNumber\":99}","timestamp":1000}""")
        )
        advanceUntilIdle()

        assertFalse("must not trigger a fetch after stop", fetchInvoked)
        assertFalse(
            "must not emit a notification-received event after stop",
            observer.events.any { it.type == ObservableEventType.STREAMING_NOTIFICATION_RECEIVED }
        )
    }

    @Test
    fun `push transition is not cancelled by a rapidly following eval notification`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        val gate = CompletableDeferred<Unit>()
        var pushEnabledCompleted = false
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            onPushEnabled = {
                gate.await()
                pushEnabledCompleted = true
            },
        )

        manager.start()
        advanceUntilIdle()

        // Bring push down, then start a resume whose onPushEnabled suspends on the gate.
        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"control_pri","data":"{\"type\":\"CONTROL\",\"controlType\":\"STREAMING_PAUSED\"}","timestamp":1000}""")
        )
        advanceUntilIdle()
        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"control_pri","data":"{\"type\":\"CONTROL\",\"controlType\":\"STREAMING_RESUMED\"}","timestamp":2000}""")
        )
        advanceUntilIdle() // onPushEnabled now suspended on gate (controlHandlerJob)

        // An eval notification arrives (uses notificationHandlerJob) — must not cancel the
        // in-flight push transition.
        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"evaluations","data":"{\"type\":\"EVALUATIONS_UPDATE\",\"changeNumber\":7}","timestamp":3000}""")
        )
        advanceUntilIdle()

        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue("onPushEnabled must complete despite the eval notification", pushEnabledCompleted)
    }

    @Test
    fun `stop during in-flight fallback transition cancels controlHandlerJob`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        val gate = CompletableDeferred<Unit>()
        var pushDisabledCompleted = false
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            onPushDisabled = {
                gate.await()
                pushDisabledCompleted = true
            },
        )

        manager.start()
        advanceUntilIdle()

        // Occupancy zero starts onPushDisabled which suspends on the gate.
        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"[?occupancy=metrics.publishers]control_pri","data":"{\"metrics\":{\"publishers\":0}}","timestamp":1000}""")
        )
        advanceUntilIdle()

        manager.stop()
        advanceUntilIdle()

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse("onPushDisabled must be cancelled by stop", pushDisabledCompleted)
    }

    @Test
    fun `control-paused survives background-foreground and resume re-enables push`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        var pushEnabledCount = 0
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            onPushEnabled = { pushEnabledCount++ },
        )

        manager.start()
        advanceUntilIdle()

        // Control pause -> push down.
        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"control_pri","data":"{\"type\":\"CONTROL\",\"controlType\":\"STREAMING_PAUSED\"}","timestamp":1000}""")
        )
        advanceUntilIdle()

        // Background then foreground: socket reconnects but control pause persists.
        manager.pause()
        advanceUntilIdle()
        manager.resume()
        advanceUntilIdle()

        assertEquals("reconnect must not re-enable push while control-paused", 0, pushEnabledCount)

        // Server resumes streaming.
        eventSourceClient.simulateMessage(
            mapOf("data" to """{"channel":"control_pri","data":"{\"type\":\"CONTROL\",\"controlType\":\"STREAMING_RESUMED\"}","timestamp":2000}""")
        )
        advanceUntilIdle()

        assertEquals(1, pushEnabledCount)
    }

    @Test
    fun `single transient drop reconnects without falling back to polling`() = runTest {
        var connectCount = 0
        val clients = mutableListOf<FakeEventSourceClient>()
        var pushDisabledCount = 0
        val manager = createManager(
            eventSourceClientProvider = {
                connectCount++
                FakeEventSourceClient().also { clients.add(it) }
            },
            backoffCounter = FakeBackoffCounter(delays = listOf(1)),
            onPushDisabled = { pushDisabledCount++ },
        )

        manager.start()
        advanceUntilIdle()

        // First drop reconnects on the first retry — the grace period must hold.
        clients.last().simulateError(retryable = true)
        advanceUntilIdle()

        assertTrue("should have reconnected", connectCount >= 2)
        assertEquals("a single transient drop must not start polling", 0, pushDisabledCount)
    }

    @Test
    fun `second consecutive reconnect failure falls back to polling and recovers on reconnect`() = runTest {
        var connectCount = 0
        val clients = mutableListOf<FakeEventSourceClient>()
        var pushDisabledCount = 0
        var pushEnabledCount = 0
        var fetchCount = 0
        val observer = FakeCompositeObserver()
        val manager = createManager(
            eventSourceClientProvider = {
                connectCount++
                FakeEventSourceClient().also {
                    // start (1) opens; first retry (2) fails; second retry (3) opens.
                    if (connectCount == 2) it.shouldFailConnect = true
                    clients.add(it)
                }
            },
            backoffCounter = FakeBackoffCounter(delays = listOf(1, 1, 1)),
            onPushDisabled = { pushDisabledCount++ },
            onPushEnabled = { pushEnabledCount++ },
            onEvaluationFetchNotification = { fetchCount++ },
            observer = observer,
        )

        manager.start()
        advanceUntilIdle()
        assertEquals("initial connect catch-up fetch", 1, fetchCount)

        clients.first().simulateError(retryable = true)
        advanceUntilIdle()

        assertEquals("polling started once on the second failure", 1, pushDisabledCount)
        assertEquals("streaming resumed once on reconnect", 1, pushEnabledCount)
        // Recovery catch-up is owned by onPushEnabled, not an extra explicit fetch.
        assertEquals("no double catch-up fetch on reconnect", 1, fetchCount)

        val syncEvents = observer.events.filter { it.type == ObservableEventType.RUNTIME_SYNC_MODE_CHANGED }
        assertTrue(
            "fallback emits CONNECTION_RETRY",
            syncEvents.any { it.properties["to"] == "POLLING_FALLBACK" && it.properties["reason"] == "CONNECTION_RETRY" }
        )
        assertTrue(
            "recovery emits CONNECTION_RECOVERED",
            syncEvents.any { it.properties["to"] == "STREAMING" && it.properties["reason"] == "CONNECTION_RECOVERED" }
        )
    }

    @Test
    fun `non-retryable error stops streaming and falls back to polling`() = runTest {
        var connectCount = 0
        val clients = mutableListOf<FakeEventSourceClient>()
        var pushDisabledCount = 0
        val observer = FakeCompositeObserver()
        val manager = createManager(
            eventSourceClientProvider = {
                connectCount++
                FakeEventSourceClient().also { clients.add(it) }
            },
            onPushDisabled = { pushDisabledCount++ },
            observer = observer,
        )

        manager.start()
        advanceUntilIdle()

        clients.last().simulateError(retryable = false)
        advanceUntilIdle()

        assertEquals("must not reconnect after a non-retryable error", 1, connectCount)
        assertEquals("must switch to polling", 1, pushDisabledCount)
        assertTrue(
            "emits CONNECTION_NON_RETRYABLE",
            observer.events.any {
                it.type == ObservableEventType.RUNTIME_SYNC_MODE_CHANGED &&
                    it.properties["reason"] == "CONNECTION_NON_RETRYABLE"
            }
        )
        assertTrue("socket must be torn down", clients.last().disconnectCalled)
    }

    @Test
    fun `reconnect while control-paused does not re-enable push or catch up`() = runTest {
        var connectCount = 0
        val clients = mutableListOf<FakeEventSourceClient>()
        var pushEnabledCount = 0
        var fetchCount = 0
        val manager = createManager(
            eventSourceClientProvider = {
                connectCount++
                FakeEventSourceClient().also {
                    if (connectCount == 2) it.shouldFailConnect = true
                    clients.add(it)
                }
            },
            backoffCounter = FakeBackoffCounter(delays = listOf(1, 1, 1)),
            onPushEnabled = { pushEnabledCount++ },
            onEvaluationFetchNotification = { fetchCount++ },
        )

        manager.start()
        advanceUntilIdle()
        clients.first().simulateMessage(
            mapOf("data" to """{"channel":"control_pri","data":"{\"type\":\"CONTROL\",\"controlType\":\"STREAMING_PAUSED\"}","timestamp":1000}""")
        )
        advanceUntilIdle()
        fetchCount = 0 // ignore the initial catch-up

        // Socket drops and reconnects (attempt 2 fails, attempt 3 opens) while still paused.
        clients.first().simulateError(retryable = true)
        advanceUntilIdle()

        assertEquals("must not re-enable push while control-paused", 0, pushEnabledCount)
        assertEquals("must not catch up while control-paused (polling owns refresh)", 0, fetchCount)
    }

    @Test
    fun `token-expired error frame invalidates token and drives a single reconnect`() = runTest {
        var connectCount = 0
        val clients = mutableListOf<FakeEventSourceClient>()
        var invalidateCount = 0
        var pushDisabledCount = 0
        val observer = FakeCompositeObserver()
        val manager = createManager(
            eventSourceClientProvider = {
                connectCount++
                FakeEventSourceClient().also { clients.add(it) }
            },
            backoffCounter = FakeBackoffCounter(delays = listOf(1)),
            invalidateToken = { invalidateCount++ },
            onPushDisabled = { pushDisabledCount++ },
            observer = observer,
        )

        manager.start()
        advanceUntilIdle()
        val firstClient = clients.first()

        // Frame-driven recovery: the SDK reconnects off the error frame itself, WITHOUT the server
        // closing the socket (FakeEventSourceClient does not auto-fire onError on a message).
        firstClient.simulateMessage(
            mapOf(
                "event" to "error",
                "data" to """{"code":40142,"statusCode":401,"message":"Token expired"}""",
            )
        )
        advanceUntilIdle()

        assertEquals("token must be invalidated once", 1, invalidateCount)
        assertTrue("dead socket must be torn down", firstClient.disconnectCalled)
        assertEquals("must reconnect off the frame", 2, connectCount)
        assertEquals("a single error must not blip to polling fallback", 0, pushDisabledCount)
        val notifEvent = observer.events.find { it.type == ObservableEventType.STREAMING_NOTIFICATION_RECEIVED && it.properties["notificationType"] == "STREAMING_ERROR" }
        assertNotNull("must emit STREAMING_ERROR notification event", notifEvent)
        assertEquals("40142", notifEvent!!.properties["errorCode"])
    }

    @Test
    fun `token-expired error frame plus socket close coalesce into a single reconnect`() = runTest {
        var connectCount = 0
        val clients = mutableListOf<FakeEventSourceClient>()
        var invalidateCount = 0
        var pushDisabledCount = 0
        val observer = FakeCompositeObserver()
        val manager = createManager(
            eventSourceClientProvider = {
                connectCount++
                FakeEventSourceClient().also { clients.add(it) }
            },
            backoffCounter = FakeBackoffCounter(delays = listOf(1)),
            invalidateToken = { invalidateCount++ },
            onPushDisabled = { pushDisabledCount++ },
            observer = observer,
        )

        manager.start()
        advanceUntilIdle()

        // Error frame invalidates the token; then the server closes the socket (single onError).
        clients.first().simulateMessage(
            mapOf(
                "event" to "error",
                "data" to """{"code":40142,"statusCode":401,"message":"Token expired"}""",
            )
        )
        clients.first().simulateError(retryable = true)
        advanceUntilIdle()

        assertEquals("token invalidated once", 1, invalidateCount)
        assertEquals("exactly one reconnect (no double-trigger)", 2, connectCount)
        assertEquals("a single drop must not blip to polling fallback", 0, pushDisabledCount)
    }

    @Test
    fun `non-token error frame is observed only, no invalidate or reconnect`() = runTest {
        var connectCount = 0
        val clients = mutableListOf<FakeEventSourceClient>()
        var invalidateCount = 0
        val observer = FakeCompositeObserver()
        val manager = createManager(
            eventSourceClientProvider = {
                connectCount++
                FakeEventSourceClient().also { clients.add(it) }
            },
            invalidateToken = { invalidateCount++ },
            observer = observer,
        )

        manager.start()
        advanceUntilIdle()

        clients.first().simulateMessage(
            mapOf(
                "event" to "error",
                "data" to """{"code":50000,"statusCode":500,"message":"Server error"}""",
            )
        )
        advanceUntilIdle()

        assertEquals("must not invalidate token", 0, invalidateCount)
        assertEquals("must not reconnect", 1, connectCount)
        assertFalse("must not tear down the socket", clients.first().disconnectCalled)
        assertNotNull(
            "must still emit STREAMING_ERROR notification event",
            observer.events.find { it.properties["notificationType"] == "STREAMING_ERROR" }
        )
    }

    @Test
    fun `normal message with event=message still parses and triggers fetch`() = runTest {
        val eventSourceClient = FakeEventSourceClient()
        var fetchCount = 0
        val manager = createManager(
            eventSourceClientProvider = { eventSourceClient },
            onEvaluationFetchNotification = { fetchCount++ },
        )

        manager.start()
        advanceUntilIdle()
        fetchCount = 0 // ignore the initial catch-up

        eventSourceClient.simulateMessage(
            mapOf(
                "event" to "message",
                "data" to """{"channel":"evaluations","data":"{\"type\":\"EVALUATIONS_UPDATE\",\"changeNumber\":42}","timestamp":1000}""",
            )
        )
        advanceUntilIdle()

        assertEquals("normal message must still drive a fetch", 1, fetchCount)
    }

    private fun TestScope.createManager(
        eventSourceClientProvider: () -> FakeEventSourceClient = { FakeEventSourceClient() },
        backoffCounter: FakeBackoffCounter = FakeBackoffCounter(),
        onPushDisabled: suspend () -> Unit = {},
        onPushEnabled: suspend () -> Unit = {},
        onEvaluationFetchNotification: suspend (EvaluationUpdateNotification?) -> Unit = {},
        invalidateToken: suspend () -> Unit = {},
        channelExtractor: (String) -> List<String> = { listOf("evaluations", "[?occupancy=metrics.publishers]control_pri") },
        observer: CompositeObserver = FakeCompositeObserver(),
        evalChangeNumberHolder: AtomicLong = AtomicLong(Long.MIN_VALUE),
    ): StreamingConnectionManager = StreamingConnectionManager(
        streamingUrl = "https://streaming.test.io/sse",
        tokenProvider = { StreamingToken("test-token") },
        channelExtractor = channelExtractor,
        eventSourceClientProvider = eventSourceClientProvider,
        backoffCounter = backoffCounter,
        scope = this,
        connectionDispatcher = UnconfinedTestDispatcher(testScheduler),
        onEvaluationFetchNotification = onEvaluationFetchNotification,
        onPushDisabled = onPushDisabled,
        onPushEnabled = onPushEnabled,
        invalidateToken = invalidateToken,
        observer = observer,
        evalChangeNumberHolder = evalChangeNumberHolder,
    )
}
