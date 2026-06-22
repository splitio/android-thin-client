package io.split.client.thin.e2e

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.split.client.thin.Key
import io.split.client.thin.SdkKey
import io.split.client.thin.SdkReadyMetadata
import io.split.client.thin.SdkUpdateMetadata
import io.split.client.thin.SplitClient
import io.split.client.thin.SplitClientConfig
import io.split.client.thin.SplitEventListener
import io.split.client.thin.SplitFactory
import io.split.client.thin.SplitFactoryBuilder
import io.split.client.thin.Target
import io.split.client.thin.consumer.BuildConfig
import io.split.client.thin.internal.evaluation.DefaultSyncDelayCalculator
import io.split.client.thin.splitClientConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
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

    @Before
    fun ensureAppForeground() {
        foregroundApp()
    }

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
            assertTrue("expected isInitialCacheLoad == true on fresh init",
                listener.lastReadyMetadata?.isInitialCacheLoad == true)
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    /**
     * Given the SDK is configured in POLLING mode with a target that has attributes
     * When the factory reaches onReady
     * Then the evaluations request includes an X-Harness-FME-Content-Digest header
     * And the digest value is a truncated SHA-512 of the canonical attribute payload
     * And the request includes an X-Harness-FME-SDK-Version header matching the build config
     */
    @Test
    fun evaluationsRequestIncludesTruncatedSha512ContentDigestForAttributes() {
        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))

        var capturedDigest: String? = null
        var capturedVersion: String? = null
        server.evaluationsHandler = { request ->
            capturedDigest = request.getHeader("X-Harness-FME-Content-Digest")
            capturedVersion = request.getHeader("X-Harness-FME-SDK-Version")
            MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1)
        }

        val target = Target(
            key = Key("user1", bucketingKey = "bucket"),
            attributes = mapOf(
                "country" to "arg",
                "age" to 200,
                "version" to "3.0.0",
                "colors" to listOf("red", "green")
            ),
            trafficType = "user",
        )
        val factory = buildPollingFactory(server, prefix = "e2e_digest_$RUN_ID", defaultTarget = target)
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())
            assertEquals(
                "S1cE3SGkxSc",
                capturedDigest,
            )
            assertEquals(BuildConfig.THIN_CLIENT_VERSION_HEADER, capturedVersion)
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
            val user = request.evaluationsKey()
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
            assertNotNull("SDK_UPDATE metadata should be present when flags change", listener.lastUpdateMetadata)
            assertEquals(SdkUpdateMetadata.Type.FLAGS_UPDATE, listener.lastUpdateMetadata?.type)
            assertTrue("changed flags should include flag_a",
                listener.lastUpdateMetadata?.names?.contains("flag_a") == true)
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
    // Test CP1 — Control PAUSED keeps the SSE socket open and falls back to polling
    // -------------------------------------------------------------------------

    /**
     * Given the SDK is in STREAMING mode with a 1-second fallback polling rate
     * And the server sends STREAMING_PAUSED over the open SSE connection
     * Then the SSE socket is NOT re-opened (sseConnectionCount stays 1)
     * And fallback polling becomes live and drives onUpdate when evaluations change
     */
    @Test
    fun controlPausedKeepsSocketOpenAndFallsBackToPolling() {
        val server = MockSplitServer()
        repeat(5) { server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_ENABLED)) }

        // All fetches return RESPONSE_1 until the test flips this; keeps the one-shot update
        // latch armed through init, the onOpen catch-up, and the pause-triggered refetch.
        val returnUpdated = AtomicBoolean(false)
        server.evaluationsHandler = {
            if (returnUpdated.get()) MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2)
            else MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1)
        }

        // One SSE connection: PAUSED ~2s in, then the socket stays open.
        server.enqueueSse(
            server.buildTimedSseResponse(listOf(2L to E2EFixtures.sseControlPaused(1_000)))
        )

        val factory = buildStreamingFactory(server, prefix = "e2e_ctrl_paused_$RUN_ID", pollingRate = 1)
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())
            assertEquals("on", client.getTreatment("flag_a").treatment)

            val sseDeadline = System.currentTimeMillis() + 5_000
            while (server.sseConnectionCount.get() == 0 && System.currentTimeMillis() < sseDeadline) {
                Thread.sleep(50)
            }
            assertTrue("SSE should have connected", server.sseConnectionCount.get() >= 1)

            // Allow PAUSED (~2s) to arrive and fallback polling to start; all fetches so far
            // return RESPONSE_1 so no premature onUpdate.
            Thread.sleep(3_500)

            // Now changes are visible: the next poll drives an update.
            returnUpdated.set(true)
            assertTrue("onUpdate did not fire from polling fallback", listener.awaitUpdate(15))
            assertEquals("off", client.getTreatment("flag_a").treatment)

            // Control pause must not have re-opened the socket.
            assertEquals("control pause must keep the same SSE socket", 1, server.sseConnectionCount.get())

            // Polling is live: requests keep growing across a poll window.
            val before = server.evaluationRequestCount.get()
            Thread.sleep(2_500)
            assertTrue(
                "polling not live after control pause",
                server.evaluationRequestCount.get() > before,
            )
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Test CF1 — Repeated SSE connection failures fall back to polling, then recover
    // -------------------------------------------------------------------------

    /**
     * Given the SDK is in STREAMING mode with a 1-second fallback polling rate
     * And the SSE endpoint fails to connect repeatedly (HTTP 500 — retryable, no onOpen)
     * Then after the grace period the SDK falls back to polling and drives onUpdate
     * And once the SSE socket reconnects successfully, fallback polling stops.
     */
    @Test
    fun repeatedStreamingFailuresFallBackToPollingThenRecover() {
        val server = MockSplitServer()
        repeat(10) { server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_ENABLED)) }

        val returnUpdated = AtomicBoolean(false)
        server.evaluationsHandler = {
            if (returnUpdated.get()) MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2)
            else MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1)
        }

        // First three SSE connects fail (retryable 5xx, no onOpen). The grace period trips on
        // the second failure -> polling fallback. The fourth connect falls through to the
        // default long-lived SSE response -> onOpen -> recovery.
        repeat(3) { server.enqueueSse(MockResponse().setResponseCode(500)) }

        val factory = buildStreamingFactory(server, prefix = "e2e_conn_fail_$RUN_ID", pollingRate = 1)
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())
            assertEquals("on", client.getTreatment("flag_a").treatment)

            // Let the second failure trip the grace period and start fallback polling.
            Thread.sleep(3_500)

            // Fallback polling is live: a change now drives an update.
            returnUpdated.set(true)
            assertTrue("onUpdate did not fire from polling fallback", listener.awaitUpdate(15))
            assertEquals("off", client.getTreatment("flag_a").treatment)

            // The socket must have been retried (failures + eventual recovery).
            assertTrue(
                "streaming should have retried (count=${server.sseConnectionCount.get()})",
                server.sseConnectionCount.get() >= 3,
            )

            // Wait for the healthy reconnect (4th connect = default open SSE).
            val recoveryDeadline = System.currentTimeMillis() + 15_000
            while (server.sseConnectionCount.get() < 4 && System.currentTimeMillis() < recoveryDeadline) {
                Thread.sleep(100)
            }
            assertTrue("streaming should have recovered", server.sseConnectionCount.get() >= 4)

            // After recovery, push owns refresh again — fallback polling stops.
            Thread.sleep(2_000) // allow onPushEnabled to stop polling (+ one catch-up refetch)
            val afterRecovery = server.evaluationRequestCount.get()
            Thread.sleep(3_000) // >= 3 poll cycles
            val delta = server.evaluationRequestCount.get() - afterRecovery
            assertTrue("fallback polling should stop after streaming recovers (delta=$delta)", delta <= 1)
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Test SE1 — Streaming token-expired error frame refreshes token and reconnects
    // -------------------------------------------------------------------------

    /**
     * Given the SDK is in STREAMING mode with a long-lived (year-2099) JWT
     * And the SSE endpoint delivers a bare `event: error` 40142 ("Token expired") frame
     * And the socket is kept OPEN after the frame (so recovery cannot be EOF-driven)
     * When the frame arrives over the open socket
     * Then the SDK acts on the frame itself: the token is invalidated and re-fetched (auth called
     *   again despite the far-future exp)
     * And the SSE socket reconnects (rather than dropping to polling)
     * And a subsequent EVALUATION_UPDATE over the new socket still drives onUpdate (push is live)
     */
    @Test
    fun streamingTokenExpiredErrorFrameRefreshesTokenAndReconnects() {
        val server = MockSplitServer()
        repeat(5) { server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_ENABLED)) }

        val returnUpdated = AtomicBoolean(false)
        server.evaluationsHandler = {
            if (returnUpdated.get()) MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2)
            else MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1)
        }

        // Connection 1: delivers the token-expired error frame ~2s after open, then stays OPEN.
        // Keeping the socket open proves recovery is driven by the frame's content, not by the
        // server closing the stream (EOF) — the SDK must act on the 40142 itself to reconnect.
        server.enqueueSse(
            server.buildRawSseResponse(
                listOf(E2EFixtures.SSE_ERROR_TOKEN_EXPIRED),
                delaySeconds = 2,
                keepOpenSeconds = 60,
            )
        )
        // Connection 2 (the reconnect): long-lived, delivers an EVALUATION_UPDATE ~3s after open.
        server.enqueueSse(
            server.buildTimedSseResponse(listOf(3L to E2EFixtures.SSE_EVALUATION_UPDATE))
        )

        val factory = buildStreamingFactory(server, prefix = "e2e_token_expired_$RUN_ID", pollingRate = 1)
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())
            assertEquals("on", client.getTreatment("flag_a").treatment)

            val authCountBeforeError = server.authRequestCount.get()

            // Wait for the error-driven reconnect: a new SSE connection plus a fresh token fetch.
            val reconnectDeadline = System.currentTimeMillis() + 15_000
            while ((server.sseConnectionCount.get() < 2 || server.authRequestCount.get() <= authCountBeforeError) &&
                System.currentTimeMillis() < reconnectDeadline
            ) {
                Thread.sleep(100)
            }
            assertTrue(
                "SSE should reconnect after the token-expired error (count=${server.sseConnectionCount.get()})",
                server.sseConnectionCount.get() >= 2,
            )
            assertTrue(
                "token must be re-fetched despite the far-future JWT exp",
                server.authRequestCount.get() > authCountBeforeError,
            )

            // Push is live again on the new socket: the EVALUATION_UPDATE drives onUpdate.
            returnUpdated.set(true)
            assertTrue("onUpdate did not fire from SSE after reconnect", listener.awaitUpdate(15))
            assertEquals("off", client.getTreatment("flag_a").treatment)
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Test CP2 — Control RESUMED stops polling and resumes push over the same socket
    // -------------------------------------------------------------------------

    /**
     * Given the SDK is in STREAMING mode and has fallen back to polling after STREAMING_PAUSED
     * When STREAMING_RESUMED arrives over the same SSE connection
     * Then no new SSE connection is opened
     * And fallback polling stops
     * And a subsequent SSE EVALUATION_UPDATE still drives onUpdate (push is live again)
     */
    @Test
    fun controlResumedStopsPollingAndResumesPushOverSameSocket() {
        val server = MockSplitServer()
        repeat(5) { server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_ENABLED)) }

        // Stays RESPONSE_1 through pause+resume so the update latch is preserved for the
        // post-resume SSE eval. Flipped to RESPONSE_2 right before that eval arrives.
        val returnUpdated = AtomicBoolean(false)
        server.evaluationsHandler = {
            if (returnUpdated.get()) MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2)
            else MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1)
        }

        // One SSE connection: PAUSED ~3s, RESUMED ~9s, EVALUATION_UPDATE ~15s.
        server.enqueueSse(
            server.buildTimedSseResponse(
                listOf(
                    3L to E2EFixtures.sseControlPaused(1_000),
                    9L to E2EFixtures.sseControlResumed(2_000),
                    15L to E2EFixtures.SSE_EVALUATION_UPDATE_2,
                )
            )
        )

        val factory = buildStreamingFactory(server, prefix = "e2e_ctrl_resumed_$RUN_ID", pollingRate = 1)
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())

            val sseDeadline = System.currentTimeMillis() + 5_000
            while (server.sseConnectionCount.get() == 0 && System.currentTimeMillis() < sseDeadline) {
                Thread.sleep(50)
            }
            assertTrue("SSE should have connected", server.sseConnectionCount.get() >= 1)

            // PAUSED (~3s) -> fallback polling. Confirm polling is live.
            Thread.sleep(5_000)
            val duringPause = server.evaluationRequestCount.get()
            Thread.sleep(2_000)
            assertTrue("polling should be live during control pause",
                server.evaluationRequestCount.get() > duringPause)

            // RESUMED arrives ~9s. Give it time to stop polling (+ one catch-up refetch).
            Thread.sleep(4_000)
            val afterResume = server.evaluationRequestCount.get()
            Thread.sleep(3_000) // >= 3 poll cycles
            val delta = server.evaluationRequestCount.get() - afterResume
            assertTrue("polling should have stopped after control resume (delta=$delta)", delta <= 1)

            // Push is live again: the SSE eval (~15s) drives onUpdate.
            returnUpdated.set(true)
            assertTrue("onUpdate did not fire from SSE after resume", listener.awaitUpdate(15))
            assertEquals("off", client.getTreatment("flag_a").treatment)

            // Everything happened over the same socket — no reconnect.
            assertEquals("resume must reuse the same SSE socket", 1, server.sseConnectionCount.get())
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Test CP3 — Paused -> background -> foreground stays paused, then RESUMED stops polling
    // -------------------------------------------------------------------------

    /**
     * Given the SDK is in STREAMING mode, control-paused and polling in the foreground
     * When the app backgrounds (polling pauses, socket closes) then foregrounds
     * Then polling resumes (still in fallback) and the socket reconnects but stays non-processing
     * And when STREAMING_RESUMED arrives, polling stops — the two-axis consistency guarantee.
     */
    @Test
    fun pausedSurvivesBackgroundForegroundThenResumeStopsPolling() {
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        val server = MockSplitServer()
        repeat(8) { server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_ENABLED)) }
        server.evaluationsHandler = { MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1) }

        // First SSE connection delivers PAUSED ~2s in.
        server.enqueueSse(
            server.buildTimedSseResponse(listOf(2L to E2EFixtures.sseControlPaused(1_000)))
        )

        val scenario = launchActivity()
        val factory = buildStreamingFactory(server, prefix = "e2e_ctrl_bgfg_$RUN_ID", pollingRate = 1)
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())

            val sseDeadline = System.currentTimeMillis() + 5_000
            while (server.sseConnectionCount.get() == 0 && System.currentTimeMillis() < sseDeadline) {
                Thread.sleep(50)
            }
            assertTrue("SSE should have connected", server.sseConnectionCount.get() >= 1)
            val connectionsAfterPause = server.sseConnectionCount.get()

            // PAUSED (~2s) -> fallback polling becomes live in the foreground.
            Thread.sleep(3_500)
            val beforeBg = server.evaluationRequestCount.get()
            Thread.sleep(2_000)
            assertTrue("polling should be live while paused + foreground",
                server.evaluationRequestCount.get() > beforeBg)

            // Background: polling pauses, socket closes — no reconnect during pause.
            backgroundApp(uiDevice)
            val countAtBackground = server.evaluationRequestCount.get()
            Thread.sleep(2_500)
            assertEquals("polling must pause in background",
                countAtBackground, server.evaluationRequestCount.get())

            // Enqueue the reconnect SSE that delivers RESUMED ~4s after foreground.
            server.enqueueSse(
                server.buildTimedSseResponse(listOf(4L to E2EFixtures.sseControlResumed(2_000)))
            )

            // Foreground: socket reconnects, polling resumes (still control-paused).
            InstrumentationRegistry.getInstrumentation().targetContext.startActivity(
                Intent(InstrumentationRegistry.getInstrumentation().targetContext, TestActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            uiDevice.waitForIdle(2_000)

            // SSE reconnects (a new connection) since lifecycle closed the socket.
            val reconnectDeadline = System.currentTimeMillis() + 6_000
            while (server.sseConnectionCount.get() <= connectionsAfterPause &&
                System.currentTimeMillis() < reconnectDeadline) {
                Thread.sleep(100)
            }
            assertTrue("SSE should reconnect on foreground",
                server.sseConnectionCount.get() > connectionsAfterPause)

            // Polling resumes while still control-paused.
            val beforeFgPoll = server.evaluationRequestCount.get()
            Thread.sleep(2_500)
            assertTrue("polling should resume on foreground while still control-paused",
                server.evaluationRequestCount.get() > beforeFgPoll)

            // RESUMED (~4s after reconnect) stops polling.
            Thread.sleep(4_000)
            val afterResume = server.evaluationRequestCount.get()
            Thread.sleep(3_000)
            val delta = server.evaluationRequestCount.get() - afterResume
            assertTrue("polling should stop after STREAMING_RESUMED (delta=$delta)", delta <= 1)
        } finally {
            scenario.close()
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
                    auth = server.url()
                    evaluations = server.url()
                    events = server.url()
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
                readyTimeout = 1 // 1 second — fires SDK_READY_TIMEOUT before evaluations arrive
                serviceEndpoints {
                    auth = server.url()
                    evaluations = server.url()
                    events = server.url()
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
    // Test 4b — SDK recovers and becomes ready after a timeout
    // -------------------------------------------------------------------------

    /**
     * Given the SDK is configured with a 1-second ready timeout
     * And the evaluations endpoint delays past the timeout, then responds successfully
     * When a client is obtained and a listener registered
     * Then onTimeout fires first
     * And onReady fires afterward once the delayed evaluation response arrives
     */
    @Test
    fun sdkBecomesReadyAfterTimeout() {
        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))
        // Delay the first evaluation response past the readyTimeout, then deliver it.
        server.enqueueEvaluations(
            MockResponse()
                .setBodyDelay(3, TimeUnit.SECONDS)
                .setBody(E2EFixtures.EVALUATIONS_RESPONSE_1)
        )

        val config = splitClientConfig {
            sync {
                mode = SplitClientConfig.SyncMode.POLLING
                readyTimeout = 1 // fires SDK_READY_TIMEOUT before the response arrives
                serviceEndpoints {
                    auth = server.url()
                    evaluations = server.url()
                    events = server.url()
                }
            }
            storage { this.prefix = "e2e_timeout_ready_$RUN_ID" }
        }
        val factory = SplitFactoryBuilder.build(
            context = context,
            sdkKey = SdkKey("e2e-test-key"),
            defaultTarget = Target(key = Key("user_timeout_ready"), trafficType = "user"),
            config = config,
        )
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onTimeout did not fire", listener.awaitTimeout(10))
            assertTrue("onReady did not fire after timeout recovery", listener.awaitReady(15))
            assertEquals("on", client.getTreatment("flag_a").treatment)
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
     * And the evaluations request includes user=user_2 in the request body
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
            val user = request.evaluationsKey()
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
    // Test 5b — setTarget evicts previous key from polling
    // -------------------------------------------------------------------------

    /**
     * Given a client is created with target user_1 and reaches ready with RESPONSE_1
     * And the SDK is in POLLING mode with a 1-second refresh rate
     * When setTarget is called with target user_2
     * Then periodic polling no longer fetches evaluations for user_1 (orphan evicted)
     * And periodic polling continues to fetch evaluations for user_2 (active key)
     */
    @Test
    fun setTargetEvictsPreviousKeyFromPolling() {
        val targetUser1 = Target(key = Key("user_1"), trafficType = "user")
        val targetUser2 = Target(key = Key("user_2"), trafficType = "user")

        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))

        val user1Count = AtomicInteger(0)
        val user2Count = AtomicInteger(0)
        server.evaluationsHandler = { request ->
            val user = request.evaluationsKey()
            when (user) {
                "user_1" -> {
                    user1Count.incrementAndGet()
                    MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1)
                }
                "user_2" -> {
                    user2Count.incrementAndGet()
                    MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2)
                }
                else -> MockResponse().setBody("""{"till":-1,"since":-1,"evaluations":[]}""")
            }
        }

        val factory = buildPollingFactory(server, prefix = "e2e_evict_orphan_$RUN_ID",
            defaultTarget = targetUser1, refreshRate = 1)
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())
            assertEquals("on", client.getTreatment("flag_a").treatment)

            client.setTarget(targetUser2)

            assertTrue("onUpdate did not fire after setTarget", listener.awaitUpdate())
            assertEquals("off", client.getTreatment("flag_a").treatment)

            // Reset counters after the switch completes
            val baselineUser1 = user1Count.get()
            val baselineUser2 = user2Count.get()

            // Wait for ~3 polling cycles to observe periodic refetch behavior
            Thread.sleep(3_500)

            val user1AfterSwitch = user1Count.get() - baselineUser1
            val user2AfterSwitch = user2Count.get() - baselineUser2

            assertEquals("user_1 should not be refetched after setTarget (orphan evicted)",
                0, user1AfterSwitch)
            assertTrue("user_2 should continue polling (at least 2 cycles observed), got $user2AfterSwitch",
                user2AfterSwitch >= 2)
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Test 5c — setTarget with a new matchingKey triggers a fresh JWT fetch
    // -------------------------------------------------------------------------

    /**
     * Given a client is created with target user_1 and reaches ready
     * When setTarget is called with a different matchingKey (user_2)
     * Then the auth endpoint is called a second time (JWT invalidated and refreshed for new key)
     */
    @Test
    fun setTargetRefreshesJwtForNewMatchingKey() {
        val targetUser1 = Target(key = Key("user_1"), trafficType = "user")
        val targetUser2 = Target(key = Key("user_2"), trafficType = "user")

        val server = MockSplitServer()
        // First for init, second for the invalidateAll triggered by addTarget(new)
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))

        server.evaluationsHandler = { request ->
            when (request.evaluationsKey()) {
                "user_1" -> MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1)
                "user_2" -> MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2)
                else -> MockResponse().setBody("""{"till":-1,"since":-1,"evaluations":[]}""")
            }
        }

        val factory = buildPollingFactory(server, prefix = "e2e_jwt_refresh_$RUN_ID",
            defaultTarget = targetUser1, refreshRate = 3600)
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())
            assertEquals("on", client.getTreatment("flag_a").treatment)

            val authCountBeforeSwitch = server.authRequestCount.get()

            client.setTarget(targetUser2)
            assertTrue("onUpdate did not fire after setTarget", listener.awaitUpdate())

            val deadline = System.currentTimeMillis() + 5_000L
            while (server.authRequestCount.get() <= authCountBeforeSwitch && System.currentTimeMillis() < deadline) {
                Thread.sleep(50)
            }

            assertTrue(
                "auth endpoint should have been called again after setTarget with new matchingKey",
                server.authRequestCount.get() > authCountBeforeSwitch,
            )
            assertEquals("off", client.getTreatment("flag_a").treatment)
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Test 5d — setTarget does not leave manager.flagNames empty
    // -------------------------------------------------------------------------

    /**
     * Given a client is created with target user_1 and reaches ready
     * When setTarget is called with target user_2
     * Then factory.getManager().flagNames is non-empty after the target switch
     * (Regression: old key eviction used to clear the only entry in storage,
     *  leaving the manager with a stale key that returned empty results.)
     */
    @Test
    fun setTargetDoesNotEmptyManagerFlagNames() {
        val targetUser1 = Target(key = Key("user_1"), trafficType = "user")
        val targetUser2 = Target(key = Key("user_2"), trafficType = "user")

        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))

        server.evaluationsHandler = { request ->
            when (request.evaluationsKey()) {
                "user_1" -> MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1)
                "user_2" -> MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2)
                else -> MockResponse().setBody("""{"till":-1,"since":-1,"evaluations":[]}""")
            }
        }

        val factory = buildPollingFactory(server, prefix = "e2e_manager_flags_$RUN_ID",
            defaultTarget = targetUser1, refreshRate = 3600)
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())
            assertTrue("flagNames should be non-empty after ready", factory.getManager().flagNames.isNotEmpty())

            client.setTarget(targetUser2)
            assertTrue("onUpdate did not fire after setTarget", listener.awaitUpdate())

            assertTrue(
                "flagNames should be non-empty after setTarget",
                factory.getManager().flagNames.isNotEmpty(),
            )
            assertTrue(
                "flagNames should contain flag_a",
                factory.getManager().flagNames.contains("flag_a"),
            )
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
            val user = request.evaluationsKey()
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
    // Test 7b — Auth 401 switches runtime streaming to SINGLE_SYNC
    // -------------------------------------------------------------------------

    /**
     * Given the SDK starts in STREAMING mode
     * And the initial auth and evaluations requests succeed
     * When the streaming on-open evaluation fetch receives HTTP 401 and forces credential refresh
     * And that credential refresh returns HTTP 401 (bad SDK key)
     * Then the SDK stops streaming and behaves like SINGLE_SYNC at runtime
     * And lifecycle resume does not reconnect streaming or process queued SSE updates
     */
    @Test
    fun auth401DuringCredentialRefreshStopsStreamingRuntimeSync() {
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_ENABLED))
        server.enqueueAuth(MockResponse().setResponseCode(401))

        var callCount = 0
        server.evaluationsHandler = {
            callCount++
            when (callCount) {
                1 -> MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1)
                2 -> MockResponse().setResponseCode(401)
                else -> MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2)
            }
        }

        val scenario = launchActivity()
        val factory = buildStreamingFactory(
            server,
            prefix = "e2e_auth_401_single_sync_$RUN_ID",
        )
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())
            assertEquals("on", client.getTreatment("flag_a").treatment)

            val initialSseDeadline = System.currentTimeMillis() + 5_000L
            while (server.sseConnectionCount.get() == 0 && System.currentTimeMillis() < initialSseDeadline) {
                Thread.sleep(50)
            }
            assertTrue("initial SSE connection was not established", server.sseConnectionCount.get() > 0)

            val deadline = System.currentTimeMillis() + 10_000L
            while (server.authRequestCount.get() < 2 && System.currentTimeMillis() < deadline) {
                Thread.sleep(50)
            }

            assertTrue(
                "auth endpoint should have been called for init and at least one failed refresh",
                server.authRequestCount.get() >= 2,
            )

            val countAfterAuth401 = server.evaluationRequestCount.get()
            assertTrue(
                "expected at least the initial fetch and the 401-triggering streaming fetch",
                countAfterAuth401 >= 2,
            )

            val sseCountAfterAuth401 = server.sseConnectionCount.get()
            server.enqueueSse(server.buildSseResponse(listOf(E2EFixtures.SSE_EVALUATION_UPDATE)))

            uiDevice.pressHome()
            uiDevice.waitForIdle(2_000)
            Thread.sleep(1_000) // ProcessLifecycleOwner 700ms debounce + margin

            InstrumentationRegistry.getInstrumentation().targetContext.startActivity(
                Intent(InstrumentationRegistry.getInstrumentation().targetContext, TestActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            uiDevice.waitForIdle(2_000)

            assertTrue("onUpdate fired after auth 401 fallback", listener.noUpdate())
            assertEquals(
                "streaming should not reconnect after auth 401 switches runtime sync to SINGLE_SYNC",
                sseCountAfterAuth401,
                server.sseConnectionCount.get(),
            )
            assertEquals(
                "queued SSE update should not trigger an evaluations fetch after auth 401 fallback",
                countAfterAuth401,
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

            assertTrue(client.track("purchase", 99.0, mapOf("item" to "book")))
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
            assertEquals(
                BuildConfig.THIN_CLIENT_VERSION_HEADER,
                server.capturedEventVersionHeaders.last(),
            )
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

        assertTrue(client.track("checkout"))

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
    // Test 10b — Streaming start is deferred while app is backgrounded
    // -------------------------------------------------------------------------

    /**
     * Given the SDK is in STREAMING mode and the initial evaluations fetch is still in flight
     * When the app goes to background before initial sync completes
     * Then onReady can fire from the completed initial sync
     * But streaming does not connect until the app returns to foreground
     */
    @Test
    fun streamingStartAfterInitialSyncWaitsForForeground() {
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_ENABLED))
        server.enqueueEvaluations(
            MockResponse()
                .setBodyDelay(5, TimeUnit.SECONDS)
                .setBody(E2EFixtures.EVALUATIONS_RESPONSE_1)
        )

        val factory = buildStreamingFactory(server, prefix = "e2e_stream_start_bg_$RUN_ID")
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            backgroundApp(uiDevice)

            assertTrue("onReady did not fire after delayed initial sync", listener.awaitReady())
            Thread.sleep(1_000)

            assertEquals(
                "streaming connected while app was backgrounded after initial sync",
                0,
                server.sseConnectionCount.get(),
            )

            foregroundApp()

            val sseDeadline = System.currentTimeMillis() + 5_000L
            while (server.sseConnectionCount.get() == 0 && System.currentTimeMillis() < sseDeadline) {
                Thread.sleep(50)
            }
            assertTrue("streaming did not connect after returning to foreground", server.sseConnectionCount.get() > 0)
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

            // Wait until polling has actually quiesced before establishing the baseline.
            // ProcessLifecycleOwner dispatches ON_STOP after a 700ms debounce, but the real
            // ActivityManager background transition can land well after pressHome() returns on
            // a slow/loaded emulator. Rather than assume a fixed sleep covers it, poll until the
            // request count has been stable for a full poll cycle (refreshRate = 1s).
            val pauseDeadline = System.currentTimeMillis() + 10_000L
            var countAfterPause = server.evaluationRequestCount.get()
            var stableSince = System.currentTimeMillis()
            while (System.currentTimeMillis() < pauseDeadline) {
                Thread.sleep(250)
                val current = server.evaluationRequestCount.get()
                if (current != countAfterPause) {
                    countAfterPause = current
                    stableSince = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - stableSince >= 1_500) {
                    break // count unchanged for > 1 poll cycle -> polling is paused
                }
            }
            assertTrue(
                "polling never paused after backgrounding",
                System.currentTimeMillis() - stableSince >= 1_500,
            )

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

            assertTrue(client.track("lifecycle_event"))

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
                    auth = server.url()
                    evaluations = server.url()
                    events = server.url()
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
    // Test 15 — Delayed PUSH notification fetches cancelled on background
    // -------------------------------------------------------------------------

    /**
     * Given the SDK is in STREAMING mode and receives a PUSH notification with delay params
     * When the app goes to background before the delayed fetch executes
     * Then the delayed fetch is cancelled and does not execute
     * And when the app returns to foreground, evaluations can be re-fetched normally
     */
    @Test
    fun delayedPushFetchCancelledOnBackground() {
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        val server = MockSplitServer()
        repeat(5) { server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_ENABLED)) }

        // Return RESPONSE_1 initially, then RESPONSE_2 after foreground returns
        val returnUpdatedResponse = AtomicBoolean(false)
        server.evaluationsHandler = {
            if (returnUpdatedResponse.get()) MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2)
            else MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1)
        }

        val scenario = launchActivity()
        // Use a specific target key that produces a predictably long delay
        // With key="delay_test_key_xyz", seed=42, interval=120000, hash produces ~80+ second delay
        val targetKey = "delay_test_key_xyz"
        val factory = buildStreamingFactory(
            server,
            prefix = "e2e_delayed_push_cancel_$RUN_ID",
            defaultTarget = Target(key = Key(targetKey), trafficType = "user")
        )
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())

            // Wait for SSE connection
            val sseDeadline = System.currentTimeMillis() + 5_000
            while (server.sseConnectionCount.get() == 0 && System.currentTimeMillis() < sseDeadline) {
                Thread.sleep(100)
            }
            assertTrue("SSE should have connected", server.sseConnectionCount.get() >= 1)

            // Record how many evaluation fetches have occurred so far
            val fetchCountBeforePush = server.evaluationRequestCount.get()

            // Send PUSH notification with 120-second delay interval
            // Even with worst-case hash, the computed delay will be >> 5 seconds
            val longDelayEvent = """{"channel":"${E2EFixtures.STREAMING_CHANNEL}",""" +
                    """"data":"{\"type\":\"EVALUATIONS_UPDATE\",\"changeNumber\":2000,""" +
                    """\"i\":120000,\"s\":42,\"h\":1}",""" +
                    """"timestamp":1000000}"""
            server.enqueueSse(
                server.buildSseResponse(listOf(longDelayEvent), delaySeconds = 0)
            )
            Thread.sleep(500) // Let SSE message be received

            // Immediately background the app before the delay completes
            uiDevice.pressHome()
            uiDevice.waitForIdle(2_000)
            Thread.sleep(1_000) // ProcessLifecycleOwner 700ms debounce + margin

            // Wait a few seconds to ensure fetch would have happened if not cancelled properly
            Thread.sleep(3_000)

            // Verify no evaluation fetch occurred during background
            val fetchCountDuringBackground = server.evaluationRequestCount.get()
            assertEquals(
                "Delayed PUSH fetch should have been cancelled while backgrounded",
                fetchCountBeforePush,
                fetchCountDuringBackground
            )

            // Return to foreground and trigger new SSE event
            returnUpdatedResponse.set(true)
            server.enqueueSse(
                server.buildSseResponse(listOf(E2EFixtures.SSE_EVALUATION_UPDATE), delaySeconds = 0)
            )

            InstrumentationRegistry.getInstrumentation().targetContext.startActivity(
                Intent(InstrumentationRegistry.getInstrumentation().targetContext, TestActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            uiDevice.waitForIdle(2_000)

            // Verify SDK can still fetch normally after returning to foreground
            assertTrue("onUpdate should fire after foreground return", listener.awaitUpdate(15))
            assertEquals("off", client.getTreatment("flag_a").treatment)

        } finally {
            scenario.close()
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Test 16 — configsEnabled=true/false controls capabilities= on auth and withconfig= on evaluations
    // -------------------------------------------------------------------------

    /**
     * Given the SDK is configured with configsEnabled=true
     * When the factory reaches onReady
     * Then the auth request URL contains capabilities=evaluatorWithConfigs
     */
    @Test
    fun configsEnabledTrueSendsEvaluatorWithConfigsCapabilityOnAuthRequest() {
        val captured = captureRequestsAfterReady(configsEnabled = true, prefix = "e2e_dyn_cfg_auth_true_$RUN_ID")
        assertTrue("auth request should contain capabilities=evaluatorWithConfigs", captured.authQuery.contains("capabilities=evaluatorWithConfigs"))
    }

    /**
     * Given the SDK is configured with configsEnabled=true
     * When the factory reaches onReady
     * Then the evaluations request body contains "configs":true
     */
    @Test
    fun configsEnabledTrueSendsConfigsParamOnEvaluationsRequest() {
        val captured = captureRequestsAfterReady(configsEnabled = true, prefix = "e2e_dyn_cfg_eval_true_$RUN_ID")
        assertTrue("evaluations request body should contain \"configs\":true", captured.evalBody.contains("\"configs\":true"))
    }

    /**
     * Given the SDK is configured with configsEnabled=false
     * When the factory reaches onReady
     * Then the auth request URL contains capabilities=evaluator
     * And the auth request URL does not contain evaluatorWithConfigs
     */
    @Test
    fun configsEnabledFalseSendsEvaluatorCapabilityOnAuthRequest() {
        val captured = captureRequestsAfterReady(configsEnabled = false, prefix = "e2e_dyn_cfg_auth_false_$RUN_ID")
        assertTrue("auth request should contain capabilities=evaluator", captured.authQuery.contains("capabilities=evaluator"))
        assertFalse("auth request should not contain evaluatorWithConfigs", captured.authQuery.contains("evaluatorWithConfigs"))
    }

    /**
     * Given the SDK is configured with configsEnabled=false
     * When the factory reaches onReady
     * Then the evaluations request body contains "configs":false
     */
    @Test
    fun configsEnabledFalseDoesNotSendConfigsParamOnEvaluationsRequest() {
        val captured = captureRequestsAfterReady(configsEnabled = false, prefix = "e2e_dyn_cfg_eval_false_$RUN_ID")
        assertTrue("evaluations request body should contain \"configs\":false", captured.evalBody.contains("\"configs\":false"))
    }

    // Test 17 — configsEnabled change across restarts clears cached evaluations
    /**
     * Given a factory has run with configsEnabled=false and populated the Room cache
     * When a new factory is created with the same prefix but configsEnabled=true
     * Then the evaluations request uses since=-1 (cache cleared)
     * And the evaluations request body contains "configs":true
     * And the treatment config field is non-null (config was returned and stored)
     */
    @Test
    fun configsEnabledChangeAcrossRestartClearsCacheAndRefetches() {
        val server = MockSplitServer()
        val prefix = "e2e_dyn_cfg_change_$RUN_ID"

        try {
            // Phase 1: first run with configsEnabled=false — populates cache
            server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))
            server.enqueueEvaluations(MockResponse().setBody(E2EFixtures.EVALUATIONS_WITHOUT_CONFIG))

            val factory1 = buildPollingFactoryWithDynamicConfig(server, prefix, configsEnabled = false)
            val client1 = factory1.getClient()
            val listener1 = TestEventListener()
            client1.addEventListener(listener1.asSplitEventListener)

            assertTrue("Phase 1: onReady did not fire", listener1.awaitReady())
            assertEquals("Phase 1: my_feature treatment", "on", client1.getTreatment("my_feature").treatment)
            runBlocking { factory1.destroy() }

            // Phase 2: restart with configsEnabled=true — cache must be cleared and re-fetched
            server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))
            server.enqueueEvaluations(MockResponse().setBody(E2EFixtures.EVALUATIONS_WITH_CONFIG))

            val factory2 = buildPollingFactoryWithDynamicConfig(server, prefix, configsEnabled = true)
            val client2 = factory2.getClient()
            val listener2 = TestEventListener()
            client2.addEventListener(listener2.asSplitEventListener)

            assertTrue("Phase 2: onReady did not fire after config change", listener2.awaitReady())

            val evalQuery2 = server.lastEvaluationsRequest?.requestUrl?.query ?: ""
            val evalBody2 = server.lastEvaluationsRequest?.body?.readUtf8() ?: ""
            assertTrue(
                "Phase 2 evaluations request should have since=-1 (cache cleared), got: $evalQuery2",
                evalQuery2.contains("since=-1"),
            )
            assertTrue(
                "Phase 2 evaluations request body should have \"configs\":true, got: $evalBody2",
                evalBody2.contains("\"configs\":true"),
            )

            val treatment2 = client2.getTreatment("my_feature")
            assertEquals("Phase 2: my_feature treatment", "on", treatment2.treatment)
            assertNotNull("Phase 2: config should be present with configsEnabled=true", treatment2.config)

            runBlocking { factory2.destroy() }
        } finally {
            server.shutdown()
        }
    }

    // Test — flagSets config sends sets param on evaluations request (alphabetical, comma-separated)
    /**
     * Given the SDK is configured with flagSets={"set_b", "set_a"}
     * When the factory reaches onReady
     * Then the evaluations request body contains "sets":["set_a","set_b"] (alphabetically sorted)
     */
    @Test
    fun flagSetsConfigSendsSetsParamOnEvaluationsRequest() {
        val captured = captureRequestsAfterReady(
            configsEnabled = false,
            prefix = "e2e_flag_sets_param_$RUN_ID",
            flagSets = setOf("set_b", "set_a"),
        )
        assertTrue("evaluations request body should contain \"sets\":[\"set_a\",\"set_b\"], got: ${captured.evalBody}", captured.evalBody.contains("\"sets\":[\"set_a\",\"set_b\"]"))
    }

    /**
     * Given the SDK is configured with no flagSets filter
     * When the factory reaches onReady
     * Then the evaluations request body contains "sets":[] (empty array)
     */
    @Test
    fun noFlagSetsConfigDoesNotSendSetsParamOnEvaluationsRequest() {
        val captured = captureRequestsAfterReady(
            configsEnabled = false,
            prefix = "e2e_no_flag_sets_$RUN_ID",
            flagSets = null,
        )
        assertTrue("evaluations request body should contain \"sets\":[], got: ${captured.evalBody}", captured.evalBody.contains("\"sets\":[]"))
    }

    // -------------------------------------------------------------------------
    // Test — flagSets are propagated to EvaluationResult
    // -------------------------------------------------------------------------

    /**
     * Given the SDK is configured in POLLING mode and the evaluations response includes sets
     * When a client reaches onReady
     * Then getTreatment("flag_a").flagSets contains the sets from the server response
     */
    @Test
    fun getTreatment_returnsFlagSets() {
        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))
        server.enqueueEvaluations(MockResponse().setBody(E2EFixtures.EVALUATIONS_WITH_FLAG_SETS))

        val factory = buildPollingFactory(server, prefix = "e2e_flag_sets_result_$RUN_ID")
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())
            val result = client.getTreatment("flag_a")
            assertEquals(setOf("set_1", "set_2"), result.flagSets)
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Test — auth request uses repeated key= params for multiple clients
    // -------------------------------------------------------------------------

    /**
     * Given two clients are created for different targets (user_a, user_b)
     * When both reach ready (causing the auth token to be fetched with both users)
     * Then the auth request URL contains two separate key= params
     * (e.g. key=user_a&key=user_b) rather than a single composite value
     * (e.g. key=user_a%2Cuser_b)
     */
    @Test
    fun authRequestUsesRepeatedUsersParamsForMultipleClients() {
        val server = MockSplitServer()
        // Enqueue extra auth responses so both clients can get a token
        repeat(3) { server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED)) }
        server.enqueueEvaluations(MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1))
        server.enqueueEvaluations(MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2))

        val targetA = Target(key = Key("user_a"), trafficType = "user")
        val targetB = Target(key = Key("user_b"), trafficType = "user")

        val factory = buildPollingFactory(server, prefix = "e2e_auth_repeated_users_$RUN_ID",
            defaultTarget = targetA)
        val client1 = factory.getClient(targetA)
        val client2 = factory.getClient(targetB)

        val listener1 = TestEventListener()
        val listener2 = TestEventListener()
        client1.addEventListener(listener1.asSplitEventListener)
        client2.addEventListener(listener2.asSplitEventListener)

        try {
            assertTrue("client1 onReady did not fire", listener1.awaitReady())
            assertTrue("client2 onReady did not fire", listener2.awaitReady())

            // Wait for an auth request that includes both users
            val deadline = System.currentTimeMillis() + 5_000L
            var multiUserAuthQuery: String? = null
            while (multiUserAuthQuery == null && System.currentTimeMillis() < deadline) {
                multiUserAuthQuery = server.capturedAuthRequests
                    .map { it.requestUrl?.query ?: "" }
                    .firstOrNull { query ->
                        query.contains("key=user_a") && query.contains("key=user_b")
                    }
                if (multiUserAuthQuery == null) Thread.sleep(50)
            }

            assertNotNull(
                "No auth request with both key= params found. Captured queries: " +
                    server.capturedAuthRequests.map { it.requestUrl?.query },
                multiUserAuthQuery,
            )

            // The params must appear as separate repeated entries, not a composite value
            assertFalse(
                "Auth query should not use comma-joined composite value (key=user_a%2Cuser_b), got: $multiUserAuthQuery",
                multiUserAuthQuery!!.contains("%2C") || multiUserAuthQuery.contains("user_a,user_b"),
            )
            val usersValues = multiUserAuthQuery.split("&")
                .filter { it.startsWith("key=") }
                .map { it.removePrefix("key=") }
            assertTrue(
                "Expected at least 2 separate key= params, got query: $multiUserAuthQuery",
                usersValues.size >= 2,
            )
            assertEquals(
                "key= params should be in alphabetical order, got: $multiUserAuthQuery",
                usersValues.sorted(),
                usersValues,
            )
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    private data class CapturedRequests(
        val authQuery: String,
        val evalQuery: String,
        val evalBody: String,
    )

    /**
     * Builds a factory with [configsEnabled], waits for onReady, then returns the query strings
     * and body from the first auth and evaluations requests.
     */
    private fun captureRequestsAfterReady(configsEnabled: Boolean, prefix: String, flagSets: Set<String>? = null): CapturedRequests {
        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))
        server.enqueueEvaluations(MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1))

        val factory = buildPollingFactoryWithDynamicConfig(server, prefix = prefix, configsEnabled = configsEnabled, flagSets = flagSets)
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())
            return CapturedRequests(
                authQuery = server.lastAuthRequest?.requestUrl?.query ?: "",
                evalQuery = server.lastEvaluationsRequest?.requestUrl?.query ?: "",
                evalBody = server.lastEvaluationsRequest?.body?.readUtf8() ?: "",
            )
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Test — setTarget in onReadyFromCache fires SDK_READY, not SDK_UPDATE
    // -------------------------------------------------------------------------

    /**
     * Regression test for: setTarget called before initial fetch completes blocks SDK_READY.
     *
     * Given cached evaluations exist for user_a
     * And the evaluations endpoint is slow (initial fetch won't complete quickly)
     * When a client registers an onReadyFromCache listener that calls setTarget(user_b)
     * Then SDK_READY fires (for user_b's fetch using isInitialization=true)
     * And SDK_UPDATE does NOT fire (because SDK_READY had not yet fired when setTarget was called)
     */
    @Test
    fun setTargetInOnReadyFromCacheFiresSdkReadyNotSdkUpdate() {
        val cachePrefix = "e2e_set_target_on_cache_ready_$RUN_ID"
        // populateCache uses user_a (buildPollingFactory default) — initial target must match
        val targetUser1 = Target(key = Key("user_a"), trafficType = "user")
        val targetUser2 = Target(key = Key("user_b"), trafficType = "user")

        // Populate cache for user_a
        populateCache(cachePrefix)

        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))

        server.evaluationsHandler = { request ->
            when (request.evaluationsKey()) {
                "user_a" -> MockResponse().setBodyDelay(60, TimeUnit.SECONDS)
                    .setBody(E2EFixtures.EVALUATIONS_RESPONSE_1)
                "user_b" -> MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2)
                else -> MockResponse().setBody("""{"till":-1,"since":-1,"evaluations":[]}""")
            }
        }

        val factory = buildPollingFactory(server, prefix = cachePrefix, defaultTarget = targetUser1)
        val client = factory.getClient()
        val listener = TestEventListener()

        // Call setTarget in onReadyFromCache, before the initial fetch completes
        client.addEventListener(object : SplitEventListener() {
            override fun onReadyFromCache(c: SplitClient, metadata: SdkReadyMetadata?) {
                c.setTarget(targetUser2)
            }
        })
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("SDK_READY did not fire after setTarget in onReadyFromCache",
                listener.awaitReady())
            assertTrue("SDK_UPDATE should not fire (SDK_READY had not fired when setTarget was called)",
                listener.noUpdate())
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Test DB Naming — Verify global prefix + version in database filenames
    // -------------------------------------------------------------------------

    /**
     * Given the SDK is configured without a storage prefix
     * When a factory is built and reaches onReady
     * Then the database file is created with the global prefix and version
     * And the filename is `io.harness.thin.v3.{first4sdkKey}{last4sdkKey}.db`
     */
    @Test
    fun factoryWithoutPrefixCreatesDbWithGlobalPrefix() {
        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))
        server.enqueueEvaluations(MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1))

        val config = splitClientConfig {
            sync {
                mode = SplitClientConfig.SyncMode.POLLING
                pollingRate = 3600
                serviceEndpoints {
                    auth = server.url()
                    evaluations = server.url()
                    events = server.url()
                }
            }
            logLevel = SplitClientConfig.LogLevel.VERBOSE
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
            // e2e-test-key: first 4 = "e2e-", last 4 = "-key"
            val dbFile = context.getDatabasePath("io.harness.thin.v3.e2e--key.db")
            assertTrue("Database file should exist with global prefix and version", dbFile.exists())
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    /**
     * Given the SDK is configured with a storage prefix "myapp"
     * When a factory is built and reaches onReady
     * Then the database file is created with the global prefix, version, and user prefix
     * And the filename is `io.harness.thin.v3.myapp.{first4sdkKey}{last4sdkKey}.db`
     */
    @Test
    fun factoryWithPrefixCreatesDbWithGlobalAndUserPrefix() {
        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))
        server.enqueueEvaluations(MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1))

        val config = splitClientConfig {
            sync {
                mode = SplitClientConfig.SyncMode.POLLING
                pollingRate = 3600
                serviceEndpoints {
                    auth = server.url()
                    evaluations = server.url()
                    events = server.url()
                }
            }
            logLevel = SplitClientConfig.LogLevel.VERBOSE
            storage { prefix = "myapp" }
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
            // e2e-test-key: first 4 = "e2e-", last 4 = "-key"
            val dbFile = context.getDatabasePath("io.harness.thin.v3.myapp.e2e--key.db")
            assertTrue("Database file should exist with global prefix, version, and user prefix", dbFile.exists())
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun okhttp3.mockwebserver.RecordedRequest.evaluationsKey(): String =
        org.json.JSONObject(body.readUtf8()).optString("key", "")

    /** Launches [TestActivity] so [ProcessLifecycleOwner] can be driven in lifecycle tests. */
    private fun launchActivity(): ActivityScenario<TestActivity> =
        ActivityScenario.launch(Intent(context, TestActivity::class.java))

    private fun foregroundApp() {
        context.startActivity(
            Intent(context, TestActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle(2_000)
    }

    private fun backgroundApp(uiDevice: UiDevice, debounceMillis: Long = 1_000L) {
        uiDevice.pressHome()
        uiDevice.waitForIdle(2_000)
        Thread.sleep(debounceMillis)
    }

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
                pollingRate = refreshRate
                serviceEndpoints {
                    auth = server.url()
                    evaluations = server.url()
                    events = server.url()
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

    private fun buildPollingFactoryWithDynamicConfig(
        server: MockSplitServer,
        prefix: String,
        configsEnabled: Boolean,
        flagSets: Set<String>? = null,
    ): SplitFactory {
        val config = splitClientConfig {
            sync {
                mode = SplitClientConfig.SyncMode.POLLING
                pollingRate = 3600
                serviceEndpoints {
                    auth = server.url()
                    evaluations = server.url()
                    events = server.url()
                }
            }
            this.configsEnabled = configsEnabled
            logLevel = SplitClientConfig.LogLevel.VERBOSE
            storage { this.prefix = prefix }
            if (flagSets != null) filters { this.flagSets = flagSets }
        }
        return SplitFactoryBuilder.build(
            context = context,
            sdkKey = SdkKey("e2e-test-key"),
            defaultTarget = Target(key = Key("user_a"), trafficType = "user"),
            config = config,
        )
    }

    /**
     * Builds a STREAMING-mode factory pointed entirely at [server], including the SSE endpoint.
     */
    private fun buildStreamingFactory(
        server: MockSplitServer,
        prefix: String,
        defaultTarget: Target = Target(key = Key("user_a"), trafficType = "user"),
        pollingRate: Int = 3600,
    ): SplitFactory {
        val config = splitClientConfig {
            sync {
                mode = SplitClientConfig.SyncMode.STREAMING
                this.pollingRate = pollingRate
                serviceEndpoints {
                    auth = server.url()
                    evaluations = server.url()
                    events = server.url()
                    streaming = server.url()
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

    // -------------------------------------------------------------------------
    // Test A — invalid SDK key → no requests
    // -------------------------------------------------------------------------

    /**
     * Given a factory is built with a blank SDK key ("")
     * When the factory is created and 2 seconds elapse
     * Then no evaluation or auth requests are sent to the server
     * And SDK_READY never fires
     */
    @Test
    fun blankSdkKeyDoesNotSendAnyRequests() {
        val server = MockSplitServer()

        val config = splitClientConfig {
            sync {
                mode = SplitClientConfig.SyncMode.POLLING
                serviceEndpoints {
                    auth = server.url()
                    evaluations = server.url()
                    events = server.url()
                }
            }
            logLevel = SplitClientConfig.LogLevel.VERBOSE
            storage { this.prefix = "e2e_blank_key_$RUN_ID" }
        }
        val factory = SplitFactoryBuilder.build(
            context = context,
            sdkKey = SdkKey(""),
            defaultTarget = Target(key = Key("user_a"), trafficType = "user"),
            config = config,
        )
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            Thread.sleep(2_000)
            assertEquals("no auth requests expected for blank SDK key",
                0, server.authRequestCount.get())
            assertEquals("no evaluation requests expected for blank SDK key",
                0, server.evaluationRequestCount.get())
            assertFalse("SDK_READY should not fire for blank SDK key", listener.isReadyFired)
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Test B — setTarget with invalid matchingKey → no evaluation requests fired
    // -------------------------------------------------------------------------

    /**
     * Given the SDK is ready with a valid key and target
     * When setTarget is called with a blank matchingKey ("")
     * Then no new evaluation requests are sent to the server
     */
    @Test
    fun setTargetWithBlankMatchingKeyDoesNotFireEvaluationRequest() {
        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))
        server.enqueueEvaluations(MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1))

        val factory = buildPollingFactory(server, prefix = "e2e_invalid_target_$RUN_ID")
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())
            val countAfterReady = server.evaluationRequestCount.get()

            client.setTarget(Target(key = Key(""), trafficType = "user"))

            Thread.sleep(2_000)
            assertEquals("no new evaluation requests expected for blank matchingKey",
                countAfterReady, server.evaluationRequestCount.get())
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // CDN bypass tests
    // -------------------------------------------------------------------------

    /**
     * Given the SDK is in STREAMING mode and has received initial evaluations (flag_a=on, till=1000)
     * When an SSE update arrives with changeNumber=2000
     * And the CDN serves stale responses (till=1000) for the first 10 retries
     * And on the 11th attempt (with till=2000 query param) the server returns fresh data (flag_a=off)
     * Then onUpdate fires and flag_a returns "off"
     */
    @Test
    fun cdnBypassFetchesWithTillParamAfterStaleCdnResponses() {
        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_ENABLED))

        var callCount = 0
        server.evaluationsHandler = { request ->
            callCount++
            val tillParam = request.requestUrl?.queryParameter("till")
            when {
                callCount == 1 -> MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1)
                tillParam != null -> MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2)
                else -> MockResponse().setBody(E2EFixtures.EVALUATIONS_STALE)
            }
        }

        server.enqueueSse(
            server.buildSseResponse(listOf(E2EFixtures.SSE_EVALUATION_UPDATE), delaySeconds = 2)
        )

        val factory = buildStreamingFactory(server, prefix = "e2e_cdn_bypass_$RUN_ID")
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())
            assertEquals("on", client.getTreatment("flag_a").treatment)

            // Stale retries may fire onUpdate before the bypass lands — poll until treatment flips.
            val deadline = System.currentTimeMillis() + 15_000L
            var treatment = client.getTreatment("flag_a").treatment
            while (treatment != "off" && System.currentTimeMillis() < deadline) {
                Thread.sleep(200)
                treatment = client.getTreatment("flag_a").treatment
            }
            assertEquals("off", treatment)

            val bypassRequest = server.capturedEvaluationsRequests.firstOrNull {
                it.requestUrl?.queryParameter("till") != null
            }
            assertNotNull("Expected at least one request with till= query param", bypassRequest)
            assertEquals("2000", bypassRequest?.requestUrl?.queryParameter("till"))
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    /**
     * Given the SDK is in STREAMING mode
     * When an SSE update arrives with changeNumber=2000
     * And the first retry already returns fresh data (till=2000)
     * Then onUpdate fires after just one retry — no bypass needed
     */
    @Test
    fun cdnBypassStopsRetryingOnceFreshDataReceived() {
        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_ENABLED))

        var callCount = 0
        server.evaluationsHandler = { _ ->
            callCount++
            if (callCount == 1) MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1)
            else MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2)
        }

        server.enqueueSse(
            server.buildSseResponse(listOf(E2EFixtures.SSE_EVALUATION_UPDATE), delaySeconds = 2)
        )

        val factory = buildStreamingFactory(server, prefix = "e2e_cdn_bypass_fast_$RUN_ID")
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())
            assertEquals("on", client.getTreatment("flag_a").treatment)

            assertTrue("onUpdate did not fire", listener.awaitUpdate(timeoutSeconds = 10))
            assertEquals("off", client.getTreatment("flag_a").treatment)

            // Only 2 total requests: initial fetch + one retry (already fresh, no bypass needed)
            assertEquals("Expected exactly 2 evaluations requests", 2, callCount)
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    /**
     * Given the SDK is in POLLING mode with a 1-second refresh rate
     * And two clients are created for different targets (user_a, user_b) and both reach onReady
     * When client_a is destroyed via client.destroy()
     * Then periodic polling continues to fetch evaluations for user_b
     * And no polling/auth request is made for user_a after destroy (its target was deregistered)
     * And client_b still receives onUpdate when user_b evaluations change
     * And client_a receives no further events (its EventManagerObserver was unregistered)
     */
    @Test
    fun destroyingOneClientKeepsSyncAliveForRemainingClients() {
        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))

        val targetA = Target(key = Key("user_a"), trafficType = "user")
        val targetB = Target(key = Key("user_b"), trafficType = "user")

        val userACallCount = AtomicInteger(0)
        val userBCallCount = AtomicInteger(0)
        server.evaluationsHandler = { request ->
            val user = request.evaluationsKey()
            when (user) {
                "user_a" -> {
                    userACallCount.incrementAndGet()
                    MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1)
                }
                "user_b" -> {
                    val count = userBCallCount.incrementAndGet()
                    if (count == 1) MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2)
                    else MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2_UPDATED)
                }
                else -> MockResponse().setBody("""{"till":-1,"since":-1,"evaluations":[]}""")
            }
        }

        val factory = buildPollingFactory(
            server,
            prefix = "e2e_destroy_one_$RUN_ID",
            refreshRate = 1,
            defaultTarget = targetA,
        )
        val clientA = factory.getClient(targetA)
        val clientB = factory.getClient(targetB)
        val listenerA = TestEventListener()
        val listenerB = TestEventListener()
        clientA.addEventListener(listenerA.asSplitEventListener)
        clientB.addEventListener(listenerB.asSplitEventListener)

        try {
            assertTrue("clientA onReady did not fire", listenerA.awaitReady())
            assertTrue("clientB onReady did not fire", listenerB.awaitReady())

            runBlocking { clientA.destroy() }
            val userACountAfterDestroy = userACallCount.get()

            assertTrue("clientB onUpdate did not fire after clientA destroy", listenerB.awaitUpdate())
            assertTrue("clientA received spurious onUpdate after destroy", listenerA.noUpdate())
            assertEquals(
                "user_a evaluations should not be fetched after clientA destroy",
                userACountAfterDestroy,
                userACallCount.get(),
            )
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    /**
     * Given the SDK is in STREAMING mode with an established SSE connection
     * And a single default-target client has reached onReady
     * When the client is destroyed via client.destroy()
     * Then a queued SSE EVALUATION_UPDATE delivered after destroy does NOT trigger a re-fetch
     * (streaming was stopped on the last-client-destroyed callback)
     */
    @Test
    fun destroyingLastClientStopsStreaming() {
        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_ENABLED))
        server.enqueueEvaluations(MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1))
        // Subsequent evaluations for any post-destroy fetch (pre-fix path) — different payload
        // so we can also assert the treatment didn't change.
        server.enqueueEvaluations(MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2))
        // SSE event arrives ~3s after the SSE connection is opened. We destroy immediately
        // after onReady, so the event fires AFTER destroy. Pre-fix: streaming still alive →
        // event triggers a re-fetch (eval count++, treatment switches). Post-fix: SSE stopped.
        server.enqueueSse(
            server.buildSseResponse(listOf(E2EFixtures.SSE_EVALUATION_UPDATE), delaySeconds = 3)
        )

        val factory = buildStreamingFactory(server, prefix = "e2e_destroy_streaming_$RUN_ID")
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())
            assertEquals("on", client.getTreatment("flag_a").treatment)

            val deadline = System.currentTimeMillis() + 5_000L
            while (server.sseConnectionCount.get() == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(50)
            }
            assertTrue("SSE connection was not established", server.sseConnectionCount.get() > 0)

            val evalCountBeforeDestroy = server.evaluationRequestCount.get()

            runBlocking { client.destroy() }

            Thread.sleep(6_000)

            assertEquals(
                "SSE event delivered after destroy must NOT trigger an evaluations fetch " +
                    "(streaming should have been stopped on destroy)",
                evalCountBeforeDestroy,
                server.evaluationRequestCount.get(),
            )
            assertEquals(
                "a destroyed client is unusable and must return control (no fallback configured)",
                "control",
                client.getTreatment("flag_a").treatment,
            )
        } finally {
            runBlocking { runCatching { factory.destroy() } }
            server.shutdown()
        }
    }

    /**
     * Given the SDK is in POLLING mode with a 1-second refresh rate
     * And a single default-target client has reached onReady
     * When the client is destroyed via client.destroy()
     * Then no further evaluation fetches occur (polling stopped on last-client-destroyed)
     */
    @Test
    fun destroyingLastClientStopsPolling() {
        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))
        server.evaluationsHandler = {
            MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1)
        }

        val factory = buildPollingFactory(
            server,
            prefix = "e2e_destroy_polling_$RUN_ID",
            refreshRate = 1,
        )
        val client = factory.getClient()
        val listener = TestEventListener()
        client.addEventListener(listener.asSplitEventListener)

        try {
            assertTrue("onReady did not fire", listener.awaitReady())

            runBlocking { client.destroy() }
            val evalCountAfterDestroy = server.evaluationRequestCount.get()

            Thread.sleep(4_500)

            val delta = server.evaluationRequestCount.get() - evalCountAfterDestroy
            assertEquals(
                "Polling must stop after last client is destroyed (got $delta extra fetches in 4s)",
                0,
                delta,
            )
        } finally {
            runBlocking { runCatching { factory.destroy() } }
            server.shutdown()
        }
    }

    /**
     * Given two clients are ready (client1 for user_a, client2 for user_b)
     * When both track an event and flush, both events appear in the captured bodies
     * And client1 is destroyed
     * When both try to track again, only client2's event appears after flush
     * And client2 is destroyed
     * When client2 tracks again, no new event appears
     */
    @Test
    fun trackIsNoOpAfterClientDestroyed() {
        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))
        server.evaluationsHandler = { MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1) }

        val targetA = Target(key = Key("user_a"), trafficType = "user")
        val targetB = Target(key = Key("user_b"), trafficType = "user")

        val factory = buildPollingFactory(server, prefix = "e2e_track_destroy_$RUN_ID")
        val client1 = factory.getClient(targetA)
        val client2 = factory.getClient(targetB)

        val listener1 = TestEventListener()
        val listener2 = TestEventListener()
        client1.addEventListener(listener1.asSplitEventListener)
        client2.addEventListener(listener2.asSplitEventListener)

        try {
            assertTrue("client1 onReady did not fire", listener1.awaitReady())
            assertTrue("client2 onReady did not fire", listener2.awaitReady())

            // Phase 1: both clients track — expect both events
            assertTrue(client1.track("phase1_event"))
            assertTrue(client2.track("phase1_event"))
            runBlocking {
                client1.flush()
                client2.flush()
            }

            val deadline1 = System.currentTimeMillis() + 5_000L
            while (System.currentTimeMillis() < deadline1) {
                val bodies = server.capturedEventBodies
                if (bodies.any { it.contains("\"key\":\"user_a\"") && it.contains("\"eventTypeId\":\"phase1_event\"") } &&
                    bodies.any { it.contains("\"key\":\"user_b\"") && it.contains("\"eventTypeId\":\"phase1_event\"") }) break
                Thread.sleep(50)
            }
            assertTrue("client1 phase1_event not received",
                server.capturedEventBodies.any { it.contains("\"key\":\"user_a\"") && it.contains("\"eventTypeId\":\"phase1_event\"") })
            assertTrue("client2 phase1_event not received",
                server.capturedEventBodies.any { it.contains("\"key\":\"user_b\"") && it.contains("\"eventTypeId\":\"phase1_event\"") })

            // Phase 2: destroy client1 — only client2 should track
            runBlocking { client1.destroy() }

            assertFalse(client1.track("phase2_event"))
            assertTrue(client2.track("phase2_event"))
            runBlocking { client2.flush() }

            val deadline2 = System.currentTimeMillis() + 5_000L
            while (!server.capturedEventBodies.any { it.contains("\"key\":\"user_b\"") && it.contains("\"eventTypeId\":\"phase2_event\"") }
                && System.currentTimeMillis() < deadline2) {
                Thread.sleep(50)
            }
            assertTrue("client2 phase2_event not received after client1 destroyed",
                server.capturedEventBodies.any { it.contains("\"key\":\"user_b\"") && it.contains("\"eventTypeId\":\"phase2_event\"") })
            assertFalse("client1 phase2_event must not be tracked after destroy",
                server.capturedEventBodies.any { it.contains("\"key\":\"user_a\"") && it.contains("\"eventTypeId\":\"phase2_event\"") })

            // Phase 3: destroy client2 — no events should be tracked
            runBlocking { client2.destroy() }
            val countBeforePhase3 = server.capturedEventBodies.size

            assertFalse(client2.track("phase3_event"))

            Thread.sleep(2_000)

            assertEquals("no new events expected after both clients destroyed",
                countBeforePhase3, server.capturedEventBodies.size)
        } finally {
            runBlocking { runCatching { factory.destroy() } }
            server.shutdown()
        }
    }
}
