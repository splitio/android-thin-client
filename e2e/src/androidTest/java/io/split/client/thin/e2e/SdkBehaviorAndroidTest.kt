package io.split.client.thin.e2e

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Phase 2 behavioral E2E tests for the Android Thin Client SDK.
 *
 * Tests use [MockSplitServer] to intercept network traffic and [E2EFixtures] for
 * pre-built JSON payloads. All factories are pointed at the mock server via
 * [SplitClientConfig.ServiceEndpoints].
 *
 * Tests are independent: each provisions and tears down its own [MockSplitServer] and
 * factory instances.
 */
@RunWith(AndroidJUnit4::class)
class SdkBehaviorAndroidTest {

    companion object {
        /** Random suffix keeps Room DB names unique per test run, preventing cross-run pollution. */
        private val RUN_ID: String = UUID.randomUUID().toString().replace("-", "").take(8)

        private const val READY_TIMEOUT_SECONDS = 15L
        private const val UPDATE_TIMEOUT_SECONDS = 15L
        private const val NO_FIRE_TIMEOUT_SECONDS = 3L
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
     * Then onReady fires within [READY_TIMEOUT_SECONDS] seconds
     * And getTreatment("flag_a") returns "on"
     * And getTreatment("flag_b") returns "off"
     */
    @Test
    fun sdkInitializesAndReachesReady() {
        val server = MockSplitServer()
        server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))
        server.enqueueEvaluations(MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1))

        val readyLatch = CountDownLatch(1)
        var observedMetadata: SdkReadyMetadata? = null

        val factory = buildPollingFactory(server, prefix = "e2e_ready_$RUN_ID")
        val client = factory.getClient()
        client.addEventListener(object : SplitEventListener() {
            override fun onReady(client: SplitClient, metadata: SdkReadyMetadata) {
                observedMetadata = metadata
                readyLatch.countDown()
            }
        })

        try {
            assertTrue("onReady did not fire within ${READY_TIMEOUT_SECONDS}s",
                readyLatch.await(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS))

            assertEquals("on", client.getTreatment("flag_a").treatment)
            assertEquals("off", client.getTreatment("flag_b").treatment)
            assertFalse("expected isInitialCacheLoad == false",
                observedMetadata?.isInitialCacheLoad == true)
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

        // --- Part A: populate the cache by running a full ready cycle ---
        val serverA = MockSplitServer()
        serverA.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))
        serverA.enqueueEvaluations(MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1))

        val readyLatchA = CountDownLatch(1)
        val factoryA = buildPollingFactory(serverA, prefix = cachePrefix)
        val clientA = factoryA.getClient()
        clientA.addEventListener(object : SplitEventListener() {
            override fun onReady(client: SplitClient, metadata: SdkReadyMetadata) {
                readyLatchA.countDown()
            }
        })

        try {
            assertTrue("Part A: onReady did not fire", readyLatchA.await(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertEquals("Part A: flag_a treatment", "on", clientA.getTreatment("flag_a").treatment)
        } finally {
            runBlocking { factoryA.destroy() }
            serverA.shutdown()
        }

        // --- Part B: create a new factory with the same prefix; evaluations are slow ---
        val serverB = MockSplitServer()
        serverB.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))
        // Delay response by 60 s — SDK should fire onReadyFromCache from Room DB well before that
        serverB.enqueueEvaluations(MockResponse().setBodyDelay(60, TimeUnit.SECONDS)
            .setBody(E2EFixtures.EVALUATIONS_RESPONSE_1))

        val cacheReadyLatch = CountDownLatch(1)
        val networkReadyLatch = CountDownLatch(1)
        var cacheReadyFiredFirst = false
        var treatmentAtCacheReady: String? = null

        val factoryB = buildPollingFactory(serverB, prefix = cachePrefix)
        val clientB = factoryB.getClient()
        clientB.addEventListener(object : SplitEventListener() {
            override fun onReadyFromCache(client: SplitClient, metadata: SdkReadyMetadata) {
                cacheReadyFiredFirst = (networkReadyLatch.count > 0)
                treatmentAtCacheReady = client.getTreatment("flag_a").treatment
                cacheReadyLatch.countDown()
            }

            override fun onReady(client: SplitClient, metadata: SdkReadyMetadata) {
                networkReadyLatch.countDown()
            }
        })

        try {
            assertTrue("onReadyFromCache did not fire within ${READY_TIMEOUT_SECONDS}s",
                cacheReadyLatch.await(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS))

            assertTrue("onReadyFromCache should fire before onReady", cacheReadyFiredFirst)
            assertEquals("on", treatmentAtCacheReady)
        } finally {
            runBlocking { factoryB.destroy() }
            serverB.shutdown()
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

        // Track per-user call counts to serve different responses per poll round.
        val callCounts = ConcurrentHashMap<String, Int>()
        server.evaluationsHandler = { request ->
            val user = request.requestUrl?.queryParameter("user") ?: ""
            val count = callCounts.merge(user, 1, Int::plus)!!
            when {
                user == "user_a" -> MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1)
                user == "user_b" && count == 1 -> MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2)
                user == "user_b" -> MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_2_UPDATED)
                else -> MockResponse().setResponseCode(200)
                    .setBody("""{"till":-1,"since":-1,"evaluations":[]}""")
            }
        }

        val targetA = Target(key = Key("user_a"), trafficType = "user")
        val targetB = Target(key = Key("user_b"), trafficType = "user")

        // Fast polling so the second-round update fires quickly in the test
        val factory = buildPollingFactory(server, prefix = "e2e_multi_$RUN_ID", refreshRate = 1)
        val client1 = factory.getClient()
        val client2 = factory.getClient(targetB)

        val ready1Latch = CountDownLatch(1)
        val ready2Latch = CountDownLatch(1)
        val update1Latch = CountDownLatch(1)
        val update2Latch = CountDownLatch(1)

        client1.addEventListener(object : SplitEventListener() {
            override fun onReady(client: SplitClient, metadata: SdkReadyMetadata) {
                ready1Latch.countDown()
            }
            override fun onUpdate(client: SplitClient, metadata: SdkUpdateMetadata) {
                update1Latch.countDown()
            }
        })
        client2.addEventListener(object : SplitEventListener() {
            override fun onReady(client: SplitClient, metadata: SdkReadyMetadata) {
                ready2Latch.countDown()
            }
            override fun onUpdate(client: SplitClient, metadata: SdkUpdateMetadata) {
                update2Latch.countDown()
            }
        })

        try {
            assertTrue("client1 onReady did not fire",
                ready1Latch.await(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertTrue("client2 onReady did not fire",
                ready2Latch.await(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS))

            assertEquals("on", client1.getTreatment("flag_a").treatment)
            assertEquals("off", client2.getTreatment("flag_a").treatment)

            // Wait for client2's update (second poll round returns RESPONSE_2_UPDATED)
            assertTrue("client2 onUpdate did not fire within ${UPDATE_TIMEOUT_SECONDS}s",
                update2Latch.await(UPDATE_TIMEOUT_SECONDS, TimeUnit.SECONDS))

            // client1's evaluations never changed — onUpdate must NOT have fired
            assertFalse("client1 onUpdate fired unexpectedly (event isolation failure)",
                update1Latch.await(NO_FIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        } finally {
            runBlocking { factory.destroy() }
            server.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a POLLING-mode factory pointed entirely at [server].
     *
     * All five service endpoints (auth, evaluations, events, telemetry, streaming) are
     * routed to the mock server so no real Split backend is contacted.
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
