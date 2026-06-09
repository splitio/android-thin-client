package io.split.client.thin.internal.cdnbypass

import io.split.client.thin.cdnbypass.BuildConfig
import kotlinx.coroutines.delay

/**
 * Retries fetches for a set of keys with exponential backoff until each has caught up to a
 * [targetChangeNumber], or until the retry budget is exhausted.
 *
 * Up to [MAX_NORMAL_RETRIES] normal attempts are made per key. Before each attempt,
 * [freshnessChecker] is consulted — if the key is already fresh the loop exits immediately
 * without making a network call. After all normal retries are exhausted, one final bypass attempt
 * is made (only if the key is still stale), with [targetChangeNumber] forwarded to [fetchAction]
 * so the caller can signal to the server that a CDN cache bypass is needed.
 *
 * @param fetchAction Performs a single fetch for [key]. Receives [targetChangeNumber] on the
 *   final bypass attempt; `null` on all normal retries.
 * @param freshnessChecker Returns whether [key] has already reached [targetChangeNumber]. Checked
 *   before each attempt to short-circuit early if another fetch already advanced the local state.
 * @param backoffBaseMs Base delay in milliseconds for exponential backoff between normal retries.
 * @param perKeyDelayProvider Optional per-key delay applied once before the first attempt.
 */
class CdnBypassFetcher(
    private val fetchAction: suspend (key: String, targetChangeNumber: Long?) -> Unit,
    private val freshnessChecker: (key: String, targetChangeNumber: Long) -> Boolean,
    private val backoffBaseMs: Long = BuildConfig.CDN_BYPASS_BACKOFF_BASE_MS.toLong(),
    private val perKeyDelayProvider: ((key: String) -> Long)? = null,
) {
    companion object {
        const val MAX_NORMAL_RETRIES = 10
    }

    suspend fun fetch(targetChangeNumber: Long, keys: List<String>) {
        fetch(keys) { targetChangeNumber }
    }

    /**
     * Like [fetch], but reads the target change number from [targetChangeNumberProvider] at each
     * decision point instead of capturing it up-front. This lets a fetch that is parked in its
     * per-key delay (or retry backoff) catch up to the latest change number when newer streaming
     * notifications arrive while it is still in flight — without restarting the delay.
     */
    suspend fun fetch(keys: List<String>, targetChangeNumberProvider: () -> Long) {
        for (key in keys) {
            fetchForKey(key, targetChangeNumberProvider)
        }
    }

    private suspend fun fetchForKey(key: String, targetChangeNumberProvider: () -> Long) {
        val initialDelayMs = perKeyDelayProvider?.invoke(key) ?: 0L
        if (initialDelayMs > 0) delay(initialDelayMs)
        var attempt = 0
        while (attempt < MAX_NORMAL_RETRIES) {
            if (freshnessChecker(key, targetChangeNumberProvider())) return
            val backoffMs = exponentialDelayMs(attempt)
            if (backoffMs > 0) delay(backoffMs)
            fetchAction(key, null)
            attempt++
        }
        // 11th attempt: CDN bypass
        val targetChangeNumber = targetChangeNumberProvider()
        if (!freshnessChecker(key, targetChangeNumber)) {
            fetchAction(key, targetChangeNumber)
        }
    }

    private fun exponentialDelayMs(attempt: Int): Long {
        if (backoffBaseMs == 0L) return 0L
        return minOf(backoffBaseMs * (1L shl attempt), 30_000L)
    }
}
