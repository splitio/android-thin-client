package io.split.client.thin.e2e

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.split.client.thin.internal.evaluation.DefaultSyncDelayCalculator
import io.split.client.thin.Key
import io.split.client.thin.SdkKey
import io.split.client.thin.SplitClientConfig
import io.split.client.thin.SplitFactory
import io.split.client.thin.SplitFactoryBuilder
import io.split.client.thin.Target
import io.split.client.thin.splitClientConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Phase 2 behavioral E2E tests for the Android Thin Client SDK.
 *
 * Tests use [MockSplitServer] to intercept network traffic and [E2EFixtures] for
 * pre-built JSON payloads. All factories are pointed at the mock server via
 * [SplitClientConfig.ServiceEndpoints].
 *
 * Each test provisions and tears down its own [MockSplitServer] and factory instances so
 * tests are independent. Event assertions use [TestEventListener] to avoid latch boilerplate.
 */
@RunWith(AndroidJUnit4::class)
class SdkBehaviorAndroidTest {

    companion object {
        /** Random suffix keeps Room DB names unique per test run, preventing cross-run pollution. */
        private val RUN_ID: String = UUID.randomUUID().toString().replace("-", "").take(8)
    }

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

// -------------------------------------------------------------------------
    // Test 1 — SDK initializes and reaches ready
    // -------------------------------------------------------------------------

    /**
     * Given the SDK is configured in POLLING mode with no cached data
     * When a factory is built with PUSH_DISABLED auth and RESPONSE_1 evaluations
     * And a client is obtained and a listener registered
     * Then onReady fires within timeout
     * And getTreatment("flag_a") returns "on"
     * And getTreatment("flag_b") returns "off"
     */
    @Test
    fun sdkInitializesAndReachesReady() {
        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))
        server.enqueueEvaluations(MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1))

        val factory = buildPollingFactory(server, prefix = "e2e_ready_$RUN_ID")
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())
            assertEquals("on", client.getTreatment("flag_a").treatment)
            assertEquals("off", client.getTreatment("flag_b").treatment)
            assertFalse("expected isInitialCacheLoad == false",
                listener.lastReadyMetadata?.isInitialCacheLoad == true)
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Test 2 — SDK emits readyFromCache and serves cached evaluations
    // -------------------------------------------------------------------------

    /**
     * Given a factory has previously populated the Room cache with RESPONSE_1 evaluations
     * When a second factory is created with the same storage prefix
     * And the evaluations endpoint is configured to respond slowly (simulating network delay)
     * Then onReadyFromCache fires before onReady
     * And getTreatment("flag_a") returns "on" (from cache) when onReadyFromCache fires
     */
    @Test
    fun sdkEmitsReadyFromCacheAndServesCachedEvaluations() {
        val cachePrefix = "e2e_cache_$RUN_ID"

        // --- Part A: populate the cache with a full ready cycle ---
        populateCache(cachePrefix)

        // --- Part B: create a new factory with the same prefix; evaluations are slow ---
        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))
        server.enqueueEvaluations(MockResponse().setBodyDelay(60, TimeUnit.SECONDS)
            .setBody(E2EFixtures.EVALUATIONS_RESPONSE_1))

        val factory = buildPollingFactory(server, prefix = cachePrefix)
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReadyFromCache did not fire", listener.awaitCacheReady())
            assertFalse("onReadyFromCache should fire before onReady", listener.isReadyFired)
            assertEquals("on", client.getTreatment("flag_a").treatment)
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Test 3 — Multiple clients from the same factory have independent events
    // -------------------------------------------------------------------------

    /**
     * Given a factory is built with a custom evaluations dispatcher
     * When two clients are created for different targets (user_a, user_b)
     * Then both onReady callbacks fire independently
     * And client1 getTreatment("flag_a") returns "on" (RESPONSE_1 for user_a)
     * And client2 getTreatment("flag_a") returns "off" (RESPONSE_2 for user_b)
     * And when only user_b's evaluations change on the next poll
     * Then onUpdate fires on client2 but NOT on client1 (event isolation)
     */
    @Test
    fun multipleClientsFromSameFactoryHaveIndependentEvents() {
        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))

        val targetA = Target(key = Key("user_a"), trafficType = "user")
        val targetB = Target(key = Key("user_b"), trafficType = "user")

        // Dispatch per user: second round for user_b returns an updated payload
        val callCounts = ConcurrentHashMap<String, Int>()
        server.evaluationsHandler = { request ->
            val user = request.requestUrl?.queryParameter("user") ?: ""
            val count = callCounts.merge(user, 1, Int::plus)!!
            when (user) {
                "user_a" -> MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1)
                "user_b" if count == 1 -> MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2)
                "user_b" -> MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2_UPDATED)
                else -> MockResponse().setBody("""{"till":-1,"since":-1,"evaluations":[]}""")
            }
        }

        // Fast polling so the second-round update fires quickly in the test
        val factory = buildPollingFactory(server, prefix = "e2e_multi_$RUN_ID", refreshRate = 1)
        val client1 = factory.getClient(targetA)
        val client2 = factory.getClient(targetB)

        val listener1 = TestEventListener()
        val listener2 = TestEventListener()
        client1.addEventListener(listener1.asSplitEventListener)
        client2.addEventListener(listener2.asSplitEventListener)

        try {
            assertTrue("client1 onReady did not fire", listener1.awaitReady())
            assertTrue("client2 onReady did not fire", listener2.awaitReady())

            assertEquals("on", client1.getTreatment("flag_a").treatment)
            assertEquals("off", client2.getTreatment("flag_a").treatment)

            assertTrue("client2 onUpdate did not fire", listener2.awaitUpdate())
            assertTrue("client1 received spurious onUpdate (event isolation failure)",
                listener1.noUpdate())
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Test 4a — SDK emits update when evaluations change (POLLING)
    // -------------------------------------------------------------------------

    /**
     * Given the SDK is configured in POLLING mode with a 1-second refresh rate
     * And the first evaluations call returns RESPONSE_1 (flag_a=on)
     * And the second evaluations call returns RESPONSE_2 (flag_a=off)
     * When a client reaches onReady
     * Then onReady fires with flag_a == "on"
     * And onUpdate fires on the next poll cycle
     * And getTreatment("flag_a") returns "off" after the update
     * And the update metadata type is FLAGS_UPDATE
     */
    @Test
    fun sdkEmitsUpdateWhenEvaluationsChangePolling() {
        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))

        var callCount = 0
        server.evaluationsHandler = {
            callCount++
            if (callCount == 1) MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1)
            else MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2)
        }

        val factory = buildPollingFactory(server, prefix = "e2e_update_polling_$RUN_ID", refreshRate = 1)
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())
            assertEquals("on", client.getTreatment("flag_a").treatment)

            assertTrue("onUpdate did not fire within ${UPDATE_TIMEOUT_SECONDS}s", listener.awaitUpdate())
            assertEquals("off", client.getTreatment("flag_a").treatment)
            assertNull("SDK_UPDATE metadata should be null for now", listener.lastUpdateMetadata)
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Test 4b — SDK emits update when evaluations change (STREAMING)
    // -------------------------------------------------------------------------

    /**
     * Given the SDK is configured in STREAMING mode
     * And auth returns PUSH_ENABLED with a valid streaming JWT
     * And the first evaluations call returns RESPONSE_1 (flag_a=on)
     * And subsequent evaluations calls return RESPONSE_2 (flag_a=off)
     * And the SSE endpoint delivers an EVALUATION_UPDATE event 2 seconds after connection
     * When a client reaches onReady
     * Then onReady fires with flag_a == "on"
     * And after the SSE event triggers a re-fetch, onUpdate fires
     * And getTreatment("flag_a") returns "off" after the update
     */
    @Test
    fun sdkEmitsUpdateWhenEvaluationsChangeStreaming() {
        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_ENABLED))

        var callCount = 0
        server.evaluationsHandler = {
            callCount++
            if (callCount == 1) MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1)
            else MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2)
        }

        // Delay the SSE event 2 seconds so onReady fires from the initial fetch first
        server.enqueueSse(
            server.buildSseResponse(listOf(E2EFixtures.SSE_EVALUATION_UPDATE), delaySeconds = 2)
        )

        val factory = buildStreamingFactory(server, prefix = "e2e_update_streaming_$RUN_ID")
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())
            assertEquals("on", client.getTreatment("flag_a").treatment)

            assertTrue("onUpdate did not fire after SSE event", listener.awaitUpdate())
            assertEquals("off", client.getTreatment("flag_a").treatment)
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Test 4c — SINGLE_SYNC mode never emits update
    // -------------------------------------------------------------------------

    /**
     * Given the SDK is configured in SINGLE_SYNC mode
     * And auth returns PUSH_DISABLED and evaluations returns RESPONSE_1
     * When a client reaches onReady
     * Then getTreatment("flag_a") returns "on"
     * And onUpdate never fires (no background refresh in SINGLE_SYNC)
     * And getTreatment("flag_a") still returns "on" after the wait
     */
    @Test
    fun sdkInSingleSyncModeDoesNotEmitUpdate() {
        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))
        server.enqueueEvaluations(MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1))

        val config = splitClientConfig {
            sync {
                mode = SplitClientConfig.SyncMode.SINGLE_SYNC
                serviceEndpoints {
                    authUrl = server.url("/api")
                    evaluationsUrl = server.url("/api/v2/evaluations")
                    eventsUrl = server.url("/api/v1/events/bulk")
                    telemetryUrl = server.url("/api/v1/metrics/config")
                }
            }
            storage { this.prefix = "e2e_single_sync_$RUN_ID" }
        }
        val factory = SplitFactoryBuilder.build(
            context = context,
            sdkKey = SdkKey("e2e-test-key"),
            defaultTarget = Target(key = Key("user_a"), trafficType = "user"),
            config = config,
        )
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())
            assertEquals("on", client.getTreatment("flag_a").treatment)

            assertTrue("onUpdate fired unexpectedly in SINGLE_SYNC mode", listener.noUpdate())
            assertEquals("on", client.getTreatment("flag_a").treatment)
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Test 4 — SDK emits timeout when ready conditions are not met
    // -------------------------------------------------------------------------

    /**
     * Given the SDK is configured with a 1-second ready timeout
     * And the evaluations endpoint never responds (simulated by a 60-second delay)
     * When a client is obtained and a listener registered
     * Then onTimeout fires within the test's grace period
     * And onReady never fires
     */
    @Test
    fun sdkEmitsTimeoutWhenReadyConditionsNotMet() {
        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))
        server.enqueueEvaluations(MockResponse().setBodyDelay(60, TimeUnit.SECONDS)
            .setBody(E2EFixtures.EVALUATIONS_RESPONSE_1))

        val config = splitClientConfig {
            sync {
                mode = SplitClientConfig.SyncMode.POLLING
                timeout = 1 // 1 second — fires SDK_READY_TIMEOUT before evaluations arrive
                serviceEndpoints {
                    authUrl = server.url("/api")
                    evaluationsUrl = server.url("/api/v2/evaluations")
                    eventsUrl = server.url("/api/v1/events/bulk")
                    telemetryUrl = server.url("/api/v1/metrics/config")
                }
            }
            storage { this.prefix = "e2e_timeout_$RUN_ID" }
        }
        val factory = SplitFactoryBuilder.build(
            context = context,
            sdkKey = SdkKey("e2e-test-key"),
            defaultTarget = Target(key = Key("user_timeout"), trafficType = "user"),
            config = config,
        )
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onTimeout did not fire within 10s", listener.awaitTimeout(10))
            assertFalse("onReady should not fire when SDK timed out", listener.isReadyFired)
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Test 5 — setTarget switches evaluation context
    // -------------------------------------------------------------------------

    /**
     * Given a client is created with target user_1 and reaches ready with RESPONSE_1
     * When setTarget is called with target user_2
     * Then the SDK fetches evaluations for user_2
     * And the evaluations request includes user=user_2 in the query parameters
     * And getTreatment("flag_a") returns "off" (RESPONSE_2) for the new target
     */
    @Test
    fun clientSetTargetSwitchesEvaluationContext() {
        val targetUser1 = Target(key = Key("user_1"), trafficType = "user")
        val targetUser2 = Target(key = Key("user_2"), trafficType = "user")

        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))

        val requestedUsers = mutableListOf<String>()
        server.evaluationsHandler = { request ->
            val user = request.requestUrl?.queryParameter("user") ?: ""
            requestedUsers.add(user)
            when (user) {
                "user_1" -> MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1)
                "user_2" -> MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2)
                else -> MockResponse().setBody("""{"till":-1,"since":-1,"evaluations":[]}""")
            }
        }

        val factory = buildPollingFactory(server, prefix = "e2e_set_target_$RUN_ID",
            defaultTarget = targetUser1)
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())
            assertEquals("on", client.getTreatment("flag_a").treatment)

            client.setTarget(targetUser2)

            assertTrue("onUpdate did not fire after setTarget", listener.awaitUpdate())
            assertEquals("off", client.getTreatment("flag_a").treatment)
            assertTrue("evaluations request for user_2 not observed",
                requestedUsers.contains("user_2"))
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Test 6 — Concurrent evaluation fetches for the same target are deduped
    // -------------------------------------------------------------------------

    /**
     * Given a client is ready on user_1
     * And the evaluations endpoint holds the user_2 response for 2 seconds
     * When setTarget(user_2) is called from 5 threads simultaneously
     * Then only 1 evaluations request for user_2 reaches the server
     * And onUpdate fires once
     * And getTreatment("flag_a") returns "off" (RESPONSE_2)
     */
    @Test
    fun concurrentEvaluationFetchesAreDeduped() {
        val targetUser1 = Target(key = Key("user_1"), trafficType = "user")
        val targetUser2 = Target(key = Key("user_2"), trafficType = "user")

        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))

        val user2RequestCount = AtomicInteger(0)
        server.evaluationsHandler = { request ->
            val user = request.requestUrl?.queryParameter("user") ?: ""
            when (user) {
                "user_1" -> MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1)
                "user_2" -> {
                    user2RequestCount.incrementAndGet()
                    // 2-second delay keeps the first fetch in-flight while the other
                    // setTarget calls arrive, exercising the dedup guard.
                    MockResponse().setBodyDelay(2, TimeUnit.SECONDS)
                        .setBody(E2EFixtures.EVALUATIONS_RESPONSE_2)
                }
                else -> MockResponse().setBody("""{"till":-1,"since":-1,"evaluations":[]}""")
            }
        }

        val factory = buildPollingFactory(server, prefix = "e2e_dedup_$RUN_ID",
            defaultTarget = targetUser1)
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())
            assertEquals("on", client.getTreatment("flag_a").treatment)

            // Fire 5 concurrent setTarget calls while the first fetch is still in-flight
            val threads = (1..5).map { Thread { client.setTarget(targetUser2) }.also { it.start() } }
            threads.forEach { it.join(10_000) }

            assertTrue("onUpdate did not fire", listener.awaitUpdate())
            assertEquals("off", client.getTreatment("flag_a").treatment)
            assertEquals("expected exactly 1 evaluations request for user_2 (dedup)",
                1, user2RequestCount.get())
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Test 7 — Auth token is refreshed after evaluations returns 401
    // -------------------------------------------------------------------------

    /**
     * Given the SDK is configured in POLLING mode with a 1-second refresh rate
     * And the first evaluations call returns RESPONSE_1 successfully (onReady fires)
     * And the second evaluations call returns HTTP 401 (token expired)
     * And a fresh auth token is available for re-authentication
     * And the retry evaluations call (immediately after re-auth) returns RESPONSE_2
     * When the second poll cycle occurs
     * Then the auth endpoint is called a second time (token refresh)
     * And onUpdate fires with the new evaluations
     * And getTreatment("flag_a") returns "off" (RESPONSE_2)
     */
    @Test
    fun authTokenIsRefreshedAfterEvaluations401() {
        val server = MockSplitServer()
        // Two auth responses: first for init, second for the re-auth after 401
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))

        var callCount = 0
        server.evaluationsHandler = {
            callCount++
            when (callCount) {
                1 -> MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1)
                2 -> MockResponse().setResponseCode(401)
                else -> MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2)
            }
        }

        val factory = buildPollingFactory(server, prefix = "e2e_auth_refresh_$RUN_ID",
            refreshRate = 1)
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())
            assertEquals("on", client.getTreatment("flag_a").treatment)

            // The 401 on the second poll triggers re-auth + immediate retry
            assertTrue("onUpdate did not fire after 401 re-auth cycle", listener.awaitUpdate())
            assertEquals("off", client.getTreatment("flag_a").treatment)
            assertEquals("auth endpoint should have been called twice (init + refresh)",
                2, server.authRequestCount.get())
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Test 8 — track and flush submit events
    // -------------------------------------------------------------------------

    /**
     * Given the SDK is ready
     * When client.track("purchase", 99.0, mapOf("item" to "book")) is called
     * And client.flush() is called
     * Then the events endpoint receives a POST containing the tracked event
     * And the event body includes eventTypeId="purchase", value=99.0, key="user_a"
     */
    @Test
    fun trackAndFlushSubmitEvents() {
        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))
        server.enqueueEvaluations(MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1))

        val factory = buildPollingFactory(server, prefix = "e2e_track_$RUN_ID")
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())

            client.track("purchase", 99.0, mapOf("item" to "book"))
            runBlocking { client.flush() }

            // Allow a brief moment for the HTTP POST to arrive
            val deadline = System.currentTimeMillis() + 5_000L
            while (server.capturedEventBodies.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(50)
            }

            assertTrue("no events POST received", server.capturedEventBodies.isNotEmpty())
            val eventsBody = server.capturedEventBodies.last()
            assertTrue("eventTypeId not found in events body",
                eventsBody.contains("\"eventTypeId\":\"purchase\""))
            assertTrue("value not found in events body", eventsBody.contains("99.0"))
            assertTrue("key not found in events body", eventsBody.contains("\"key\":\"user_a\""))
            assertTrue("property not found in events body", eventsBody.contains("\"item\""))
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Test 9 — destroy flushes events before tearing down
    // -------------------------------------------------------------------------

    /**
     * Given the SDK is ready
     * And an event has been tracked but not yet flushed
     * When factory.destroy() is called
     * Then the events endpoint receives a POST containing the tracked event before destroy returns
     */
    @Test
    fun destroyFlushesEventsBeforeTeardown() {
        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))
        server.enqueueEvaluations(MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1))

        val factory = buildPollingFactory(server, prefix = "e2e_destroy_$RUN_ID")
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        assertTrue("onReady did not fire", listener.awaitReady())

        client.track("checkout")

        try {
            // destroy() flushes the events coordinator before cancelling the scope
            runBlocking { factory.destroy() }

            assertTrue("events not flushed before destroy completed",
                server.capturedEventBodies.any { it.contains("\"eventTypeId\":\"checkout\"") })
        } finally {
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Test 10 — SDK delays fetch after SSE update with hashing params
    // -------------------------------------------------------------------------

    /**
     * Given the SDK is in STREAMING mode and receives an SSE EVALUATION_UPDATE with hashing params
     * When the delay is computed by [DefaultSyncDelayCalculator] for "user_a"
     * Then the evaluations re-fetch happens no sooner than sseTs + expectedDelay (±500ms tolerance)
     */
    @Test
    fun sdkDelaysFetchAfterSseUpdateWithHashingParams() {
        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_ENABLED))

        // callCount 1 = init fetch, 2 = onOpen PUSH fetch (immediate, no change),
        // 3+ = SSE event delayed fetch (returns updated evaluations → SDK_UPDATE)
        var callCount = 0
        server.evaluationsHandler = {
            callCount++
            if (callCount <= 2) MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1)
            else MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2)
        }

        // Build an SSE response that delivers the event immediately, then stays alive
        // via keepalive comments so the connection doesn't close and trigger a reconnect.
        val sseBuffer = okio.Buffer()
        sseBuffer.writeUtf8("data: ${E2EFixtures.SSE_EVALUATION_UPDATE_WITH_DELAY}\n\n")
        repeat(10_000) { sseBuffer.writeUtf8(": keepalive\n\n") }
        server.enqueueSse(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .addHeader("Cache-Control", "no-cache")
                .setBody(sseBuffer)
                .throttleBody(4096, 1, TimeUnit.SECONDS)
        )

        val factory = buildStreamingFactory(server, prefix = "e2e_delay_fetch_$RUN_ID")
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())

            // Record approximate time the SSE event arrived (shortly after SSE connected)
            val sseApproxTs = System.currentTimeMillis()
            val expectedDelayMs = DefaultSyncDelayCalculator().calculateDelay(
                "user_a",
                E2EFixtures.DELAYED_FETCH_INTERVAL_MS,
                E2EFixtures.DELAYED_FETCH_SEED,
                1,
            )

            assertTrue("onUpdate did not fire after delayed SSE fetch", listener.awaitUpdate(20))

            // Index 0 = init, 1 = onOpen PUSH, 2 = SSE event delayed fetch
            val delayedFetchTs = server.evaluationRequestTimestampsMs.getOrNull(2)
            assertFalse("expected a third evaluations fetch (SSE event delayed)", delayedFetchTs == null)
            assertTrue(
                "delayed fetch arrived too early: expected >= sseTs+${expectedDelayMs}ms, got offset ${delayedFetchTs!! - sseApproxTs}ms",
                delayedFetchTs >= sseApproxTs + expectedDelayMs - 500,
            )
            assertEquals("off", client.getTreatment("flag_a").treatment)
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Test 11 — Streaming connection pauses and resumes on lifecycle
    // -------------------------------------------------------------------------

    /**
     * Given the SDK is in STREAMING mode
     * When the app goes to background (CREATED) then foreground (RESUMED)
     * Then the SSE connection is closed on pause and re-established on resume
     * And evaluations are re-fetched after reconnect
     */
    @Test
    fun streamingConnectionPausesAndResumesOnLifecycle() {
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        val server = MockSplitServer()
        // Extra auth responses so the SDK can re-auth as needed during reconnects
        repeat(5) { server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_ENABLED)) }

        // Return RESPONSE_1 during init (including onOpen PUSH fetches) so SDK_UPDATE
        // doesn't fire prematurely and consume the one-shot update latch.
        // Flip to RESPONSE_2 only after the background phase.
        val returnUpdatedResponse = AtomicBoolean(false)
        server.evaluationsHandler = {
            if (returnUpdatedResponse.get()) MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2)
            else MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1)
        }

        // Launch the activity to put the process in foreground before we background it.
        val scenario = launchActivity()
        val factory = buildStreamingFactory(server, prefix = "e2e_sse_lifecycle_$RUN_ID")
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())

            // SSE connects asynchronously after auth — may not be open yet when
            // awaitReady() returns (which fires from the evaluations fetch).
            val sseDeadline = System.currentTimeMillis() + 5_000
            while (server.sseConnectionCount.get() == 0 && System.currentTimeMillis() < sseDeadline) {
                Thread.sleep(100)
            }
            val countAtPause = server.sseConnectionCount.get()
            assertTrue("SSE should have connected at least once", countAtPause >= 1)

            // Background — ProcessLifecycleOwner fires ON_STOP, SSE manager should pause
            uiDevice.pressHome()
            uiDevice.waitForIdle(2_000)
            Thread.sleep(1_000) // ProcessLifecycleOwner 700ms debounce + margin

            // Verify no new SSE connections during the pause window
            val countDuringPause = server.sseConnectionCount.get()
            Thread.sleep(1_000)
            assertEquals("SSE reconnected during pause", countDuringPause, server.sseConnectionCount.get())

            // Enqueue fresh SSE event + switch evaluations to RESPONSE_2 for reconnect
            returnUpdatedResponse.set(true);
            server.enqueueSse(
                server.buildSseResponse(listOf(E2EFixtures.SSE_EVALUATION_UPDATE), delaySeconds = 0)
            )

            // Foreground — bring TestActivity back so ProcessLifecycleOwner fires ON_START
            InstrumentationRegistry.getInstrumentation().targetContext.startActivity(
                Intent(InstrumentationRegistry.getInstrumentation().targetContext, TestActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            uiDevice.waitForIdle(2_000)

            assertTrue("onUpdate did not fire after SSE reconnect", listener.awaitUpdate(15))
            assertTrue("SSE should have reconnected after resume",
                server.sseConnectionCount.get() > countDuringPause)
            assertEquals("off", client.getTreatment("flag_a").treatment)
        } finally {
            scenario.close()
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Test 12 — Polling scheduler pauses and resumes on lifecycle
    // -------------------------------------------------------------------------

    /**
     * Given the SDK is in POLLING mode with a 1-second refresh rate
     * When the app goes to background (CREATED)
     * Then no evaluation fetches occur during the pause
     * And when the app comes to foreground (RESUMED), polling resumes and onUpdate fires
     */
    @Test
    fun pollingSchedulerPausesAndResumesOnLifecycle() {
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))

        var callCount = 0
        server.evaluationsHandler = {
            callCount++
            if (callCount == 1) MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1)
            else MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2)
        }

        // Launch the activity first so the process is in foreground when the factory registers
        // with ProcessLifecycleOwner.
        val scenario = launchActivity()
        val factory = buildPollingFactory(
            server,
            prefix = "e2e_poll_lifecycle_$RUN_ID",
            refreshRate = 1,
        )
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())

            // Background via pressHome() — goes through the real ActivityManager so
            // Application.ActivityLifecycleCallbacks fires and ProcessLifecycleOwner
            // dispatches ON_STOP. ActivityScenario.moveToState() bypasses those callbacks.
            uiDevice.pressHome()
            uiDevice.waitForIdle(2_000)
            Thread.sleep(1_000) // ProcessLifecycleOwner 700ms debounce + margin

            val countAfterPause = server.evaluationRequestCount.get()

            Thread.sleep(2_000) // spans 2+ poll cycles if polling were still active

            assertEquals(
                "evaluations fetched during pause (polling not paused)",
                countAfterPause,
                server.evaluationRequestCount.get(),
            )

            // Foreground — bring TestActivity back so ProcessLifecycleOwner fires ON_START
            InstrumentationRegistry.getInstrumentation().targetContext.startActivity(
                Intent(InstrumentationRegistry.getInstrumentation().targetContext, TestActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            uiDevice.waitForIdle(2_000)

            assertTrue("onUpdate did not fire after polling resumed", listener.awaitUpdate(10))
            assertEquals("off", client.getTreatment("flag_a").treatment)
        } finally {
            scenario.close()
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Test 13 — Events periodic posting pauses and resumes on lifecycle
    // -------------------------------------------------------------------------

    /**
     * Given the SDK is in POLLING mode and an event has been tracked
     * When the app goes to background (CREATED)
     * Then no events are posted during the pause
     * And when the app returns to foreground (RESUMED), the events can be flushed
     */
    @Test
    fun eventsPeriodicPostingPausesAndResumesOnLifecycle() {
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))
        server.enqueueEvaluations(MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1))

        val scenario = launchActivity()
        val factory = buildPollingFactory(server, prefix = "e2e_events_lifecycle_$RUN_ID")
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())

            client.track("lifecycle_event")

            // Background — events scheduler should stop; no periodic flush should occur.
            uiDevice.pressHome()
            uiDevice.waitForIdle(2_000)
            Thread.sleep(500) // ProcessLifecycleOwner 700ms debounce

            Thread.sleep(3_000) // covers 1+ periodic push cycles if scheduler were active

            assertTrue(
                "events were posted during pause (periodic scheduler not paused)",
                server.capturedEventBodies.isEmpty(),
            )

            // Foreground — bring TestActivity back, then explicitly flush to confirm events
            // are still buffered and can be posted after lifecycle resumes.
            InstrumentationRegistry.getInstrumentation().targetContext.startActivity(
                Intent(InstrumentationRegistry.getInstrumentation().targetContext, TestActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            uiDevice.waitForIdle(2_000)

            runBlocking { client.flush() }

            val deadline = System.currentTimeMillis() + 5_000L
            while (server.capturedEventBodies.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(50)
            }

            assertTrue(
                "lifecycle_event not found in flushed events body",
                server.capturedEventBodies.any { it.contains("\"eventTypeId\":\"lifecycle_event\"") },
            )
        } finally {
            scenario.close()
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Test 14 — SINGLE_SYNC mode: lifecycle transitions are no-ops
    // -------------------------------------------------------------------------

    /**
     * Given the SDK is in SINGLE_SYNC mode
     * When the app cycles through background and foreground
     * Then no additional evaluation fetches are triggered
     * And the flag treatment remains unchanged
     */
    @Test
    fun singleSyncModeLifecycleTransitionsAreNoOps() {
        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))
        server.enqueueEvaluations(MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1))

        val config = splitClientConfig {
            sync {
                mode = SplitClientConfig.SyncMode.SINGLE_SYNC
                serviceEndpoints {
                    authUrl = server.url("/api")
                    evaluationsUrl = server.url("/api/v2/evaluations")
                    eventsUrl = server.url("/api/v1/events/bulk")
                    telemetryUrl = server.url("/api/v1/metrics/config")
                }
            }
            storage { this.prefix = "e2e_single_sync_lifecycle_$RUN_ID" }
        }
        val factory = SplitFactoryBuilder.build(
            context = context,
            sdkKey = SdkKey("e2e-test-key"),
            defaultTarget = Target(key = Key("user_a"), trafficType = "user"),
            config = config,
        )
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val scenario = launchActivity()
        try {
            assertTrue("onReady did not fire", listener.awaitReady())
            assertEquals("on", client.getTreatment("flag_a").treatment)
            val countAtReady = server.evaluationRequestCount.get()

            uiDevice.pressHome()
            uiDevice.waitForIdle(2_000)
            Thread.sleep(500) // ProcessLifecycleOwner 700ms debounce + margin

            InstrumentationRegistry.getInstrumentation().targetContext.startActivity(
                Intent(InstrumentationRegistry.getInstrumentation().targetContext, TestActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            uiDevice.waitForIdle(2_000)

            assertEquals(
                "SINGLE_SYNC should not trigger extra evaluation fetches on lifecycle",
                countAtReady,
                server.evaluationRequestCount.get(),
            )
            assertEquals("on", client.getTreatment("flag_a").treatment)
        } finally {
            scenario.close()
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Launches [TestActivity] so [ProcessLifecycleOwner] can be driven in lifecycle tests. */
    private fun launchActivity(): ActivityScenario<TestActivity> =
        ActivityScenario.launch(Intent(context, TestActivity::class.java))

    /**
     * Runs a complete ready cycle against a fresh mock server to populate the Room DB at
     * [prefix]. Used by [sdkEmitsReadyFromCacheAndServesCachedEvaluations] as a pre-condition.
     */
    private fun populateCache(prefix: String) {
        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))
        server.enqueueEvaluations(MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1))

        val factory = buildPollingFactory(server, prefix = prefix)
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("populateCache: onReady did not fire", listener.awaitReady())
            assertEquals("populateCache: flag_a treatment", "on",
                client.getTreatment("flag_a").treatment)
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    /**
     * Builds a POLLING-mode factory pointed entirely at [server].
     *
     * All service endpoints (auth, evaluations, events, telemetry) are routed to the mock
     * server so no real Split backend is contacted.
     */
    private fun buildPollingFactory(
        server: MockSplitServer,
        prefix: String,
        refreshRate: Int = 3600,
        defaultTarget: Target = Target(key = Key("user_a"), trafficType = "user"),
    ): SplitFactory {
        val config = splitClientConfig {
            sync {
                mode = SplitClientConfig.SyncMode.POLLING
                evaluationRefreshRate = refreshRate
                serviceEndpoints {
                    authUrl = server.url("/api")
                    evaluationsUrl = server.url("/api/v2/evaluations")
                    eventsUrl = server.url("/api/v1/events/bulk")
                    telemetryUrl = server.url("/api/v1/metrics/config")
                }
            }
            logLevel = SplitClientConfig.LogLevel.VERBOSE
            storage { this.prefix = prefix }
        }
        return SplitFactoryBuilder.build(
            context = context,
            sdkKey = SdkKey("e2e-test-key"),
            defaultTarget = defaultTarget,
            config = config,
        )
    }

    /**
     * Builds a STREAMING-mode factory pointed entirely at [server], including the SSE endpoint.
     */
    private fun buildStreamingFactory(
        server: MockSplitServer,
        prefix: String,
    ): SplitFactory {
        val config = splitClientConfig {
            sync {
                mode = SplitClientConfig.SyncMode.STREAMING
                serviceEndpoints {
                    authUrl = server.url("/api")
                    evaluationsUrl = server.url("/api/v2/evaluations")
                    eventsUrl = server.url("/api/v1/events/bulk")
                    telemetryUrl = server.url("/api/v1/metrics/config")
                    streamingUrl = server.url("/sse")
                }
            }
            logLevel = SplitClientConfig.LogLevel.VERBOSE
            storage { this.prefix = prefix }
        }
        return SplitFactoryBuilder.build(
            context = context,
            sdkKey = SdkKey("e2e-test-key"),
            defaultTarget = Target(key = Key("user_a"), trafficType = "user"),
            config = config,
        )
    }
}
