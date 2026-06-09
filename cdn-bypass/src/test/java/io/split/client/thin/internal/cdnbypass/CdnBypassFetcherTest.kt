package io.split.client.thin.internal.cdnbypass

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CdnBypassFetcherTest {

    // --- Freshness checking ---

    @Test
    fun `fetch is skipped when already fresh on first attempt`() = runTest {
        val fetchCalls = mutableListOf<Long?>()
        val fetcher = CdnBypassFetcher(
            fetchAction = { key, targetCn -> fetchCalls.add(targetCn) },
            freshnessChecker = { _, _ -> true },
            backoffBaseMs = 0,
        )

        fetcher.fetch(targetChangeNumber = 100L, keys = listOf("key1"))

        assertEquals(emptyList<Long?>(), fetchCalls)
    }

    @Test
    fun `fetch is called once when first attempt returns fresh data`() = runTest {
        val fetchCalls = mutableListOf<Pair<String, Long?>>()
        var callCount = 0
        val fetcher = CdnBypassFetcher(
            fetchAction = { key, targetCn ->
                fetchCalls.add(key to targetCn)
                callCount++
            },
            freshnessChecker = { _, _ -> callCount >= 1 },
            backoffBaseMs = 0,
        )

        fetcher.fetch(targetChangeNumber = 100L, keys = listOf("key1"))

        assertEquals(1, fetchCalls.size)
        assertEquals("key1" to null, fetchCalls[0])
    }

    @Test
    fun `retries up to 10 times before CDN bypass attempt`() = runTest {
        val fetchCalls = mutableListOf<Pair<String, Long?>>()
        val fetcher = CdnBypassFetcher(
            fetchAction = { key, targetCn -> fetchCalls.add(key to targetCn) },
            freshnessChecker = { _, _ -> false }, // always stale
            backoffBaseMs = 0,
        )

        fetcher.fetch(targetChangeNumber = 100L, keys = listOf("key1"))

        // 10 normal retries + 1 bypass attempt = 11 total
        assertEquals(11, fetchCalls.size)
        // First 10 have no targetChangeNumber
        fetchCalls.take(10).forEach { assertEquals(null, it.second) }
        // 11th attempt uses the bypass targetChangeNumber
        assertEquals(100L, fetchCalls[10].second)
    }

    @Test
    fun `stops retrying once fresh data is received`() = runTest {
        val fetchCalls = mutableListOf<Long?>()
        var callCount = 0
        val fetcher = CdnBypassFetcher(
            fetchAction = { _, targetCn ->
                fetchCalls.add(targetCn)
                callCount++
            },
            freshnessChecker = { _, _ -> callCount >= 3 },
            backoffBaseMs = 0,
        )

        fetcher.fetch(targetChangeNumber = 100L, keys = listOf("key1"))

        assertEquals(3, fetchCalls.size)
    }

    @Test
    fun `bypass attempt uses targetChangeNumber param`() = runTest {
        val bypassCallParams = mutableListOf<Long?>()
        val fetcher = CdnBypassFetcher(
            fetchAction = { _, targetCn -> bypassCallParams.add(targetCn) },
            freshnessChecker = { _, _ -> false }, // always stale
            backoffBaseMs = 0,
        )

        fetcher.fetch(targetChangeNumber = 42L, keys = listOf("key1"))

        assertEquals(42L, bypassCallParams.last())
    }

    // --- Multiple keys ---

    @Test
    fun `each key is fetched independently`() = runTest {
        val fetchCalls = mutableListOf<String>()
        val fetcher = CdnBypassFetcher(
            fetchAction = { key, _ -> fetchCalls.add(key) },
            freshnessChecker = { key, _ -> key == "key1" },
            backoffBaseMs = 0,
        )

        fetcher.fetch(targetChangeNumber = 100L, keys = listOf("key1", "key2"))

        // key1 is already fresh — no fetch; key2 needs 11 attempts
        assertEquals(0, fetchCalls.count { it == "key1" })
        assertEquals(11, fetchCalls.count { it == "key2" })
    }

    // --- Per-key delay ---

    @Test
    fun `applies per-key delay before first fetch`() = runTest {
        val fetched = mutableSetOf<String>()
        val fetchTimes = mutableMapOf<String, Long>()
        val fetcher = CdnBypassFetcher(
            fetchAction = { key, _ ->
                fetchTimes[key] = currentTime
                fetched.add(key)
            },
            freshnessChecker = { key, _ -> key in fetched },
            backoffBaseMs = 0,
            perKeyDelayProvider = { key -> if (key == "k1") 500L else 200L },
        )

        fetcher.fetch(targetChangeNumber = 1L, keys = listOf("k1", "k2"))

        // k1 fetched at t=500; k2 starts after k1 returns at t=500, then waits 200 → t=700
        assertEquals(500L, fetchTimes["k1"])
        assertEquals(700L, fetchTimes["k2"])
    }

    // --- Live target change number provider ---

    @Test
    fun `provider-based fetch reads latest target change number after delay`() = runTest {
        // The key is fresh only once the target reaches 200. The provider starts at 100 and is
        // bumped to 200 while the fetch is parked in its per-key delay — simulating a newer
        // notification arriving mid-flight. The fetch must catch up to 200, not stop at 100.
        var target = 100L
        var localChangeNumber = 100L
        val bypassParams = mutableListOf<Long?>()
        val fetcher = CdnBypassFetcher(
            fetchAction = { _, targetCn ->
                bypassParams.add(targetCn)
                localChangeNumber = target // a fetch brings local state up to the current target
            },
            freshnessChecker = { _, targetCn -> localChangeNumber >= targetCn },
            backoffBaseMs = 0,
            perKeyDelayProvider = { 1_000L },
        )

        // Bump the target during the parked delay window.
        val job = launch { fetcher.fetch(listOf("key1")) { target } }
        // local already at 100, so without the bump the very first freshness check would pass.
        target = 200L
        job.join()

        // One fetch happened because the live provider reported 200 > local 100 after the delay.
        assertEquals(listOf<Long?>(null), bypassParams.take(1))
        assertEquals(200L, target)
    }

    @Test
    fun `provider-based fetch skips when already fresh against live target`() = runTest {
        val fetchCalls = mutableListOf<Long?>()
        val fetcher = CdnBypassFetcher(
            fetchAction = { _, targetCn -> fetchCalls.add(targetCn) },
            freshnessChecker = { _, targetCn -> targetCn <= 5L },
            backoffBaseMs = 0,
        )

        fetcher.fetch(listOf("key1")) { 5L }

        assertEquals(emptyList<Long?>(), fetchCalls)
    }

    // --- Edge cases ---

    @Test
    fun `empty keys list results in no fetches`() = runTest {
        val fetchCalls = mutableListOf<String>()
        val fetcher = CdnBypassFetcher(
            fetchAction = { key, _ -> fetchCalls.add(key) },
            freshnessChecker = { _, _ -> false },
            backoffBaseMs = 0,
        )

        fetcher.fetch(targetChangeNumber = 100L, keys = emptyList())

        assertEquals(emptyList<String>(), fetchCalls)
    }
}
