package io.split.client.thin.e2e

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

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
            when {
                user == "user_a" -> MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1)
                user == "user_b" && count == 1 -> MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2)
                user == "user_b" -> MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2_UPDATED)
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
    // Helpers
    // -------------------------------------------------------------------------

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
