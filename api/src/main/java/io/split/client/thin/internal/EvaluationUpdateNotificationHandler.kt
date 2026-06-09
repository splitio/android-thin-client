package io.split.client.thin.internal

import io.split.client.thin.internal.cdnbypass.CdnBypassFetcher
import io.split.client.thin.internal.evaluation.EvaluationFetchCoordinator
import io.split.client.thin.internal.evaluation.EvaluationKey
import io.split.client.thin.internal.evaluation.FetchReason
import io.split.client.thin.internal.secure.EvaluationFilters
import io.split.client.thin.internal.streaming.EvaluationPayloadDecoder
import io.split.client.thin.internal.streaming.EvaluationUpdateNotification
import io.split.client.thin.internal.streaming.EvaluationUpdateStrategy.BOUNDED_FETCH_REQUEST
import io.split.client.thin.internal.streaming.EvaluationUpdateStrategy.UNBOUNDED_FETCH_REQUEST
import kotlinx.coroutines.CancellationException

internal class EvaluationUpdateNotificationHandler(
    private val decoder: EvaluationPayloadDecoder,
    private val fetchCoordinator: EvaluationFetchCoordinator,
    private val evaluationFilters: EvaluationFilters = EvaluationFilters(),
    private val delayProvider: (EvaluationUpdateNotification?) -> ((EvaluationKey) -> Long)?,
    private val onPushHandlingError: (Throwable) -> Unit = {},
    private val freshnessChecker: ((EvaluationKey) -> Long)? = null,
    private val cdnBypassBackoffBaseMs: Long = 0,
    // Supplies the latest change number seen across all streaming notifications. When provided, an
    // in-flight fetch (parked in its jitter delay) catches up to the newest change number instead of
    // the one captured when it was launched. Falls back to the notification's own change number.
    private val targetChangeNumberProvider: (() -> Long)? = null,
) {

    suspend fun handle(n: EvaluationUpdateNotification?) {
        if (n == null) return refetchAll(n)
        try {
            when (n.updateStrategy) {
                BOUNDED_FETCH_REQUEST -> handleBounded(n)
                UNBOUNDED_FETCH_REQUEST, null -> handleWithBypass(n, keyFilter = { true })
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            onPushHandlingError(t)
            refetchAll(n)
        }
    }

    private suspend fun handleBounded(n: EvaluationUpdateNotification) {
        val data = n.data ?: return refetchAll(n)
        val bitmap = decoder.decodeAsBytes(data, n.compression)
        if (bitmap.isEmpty()) return refetchAll(n)
        val predicate: (EvaluationKey) -> Boolean = { evalKey ->
            val h = decoder.hashKey(evalKey.key.matchingKey)
            val idx = decoder.computeKeyIndex(h, bitmap.size)
            decoder.isKeyInBitmap(bitmap, idx)
        }
        handleWithBypass(n, keyFilter = predicate)
    }

    private suspend fun handleWithBypass(n: EvaluationUpdateNotification, keyFilter: (EvaluationKey) -> Boolean) {
        val checker = freshnessChecker
        if (checker != null) {
            // Read the latest change number live so a fetch parked in its jitter delay catches up to
            // the newest notification rather than the change number captured when it was launched.
            val cnProvider: () -> Long = targetChangeNumberProvider?.let { provider ->
                { maxOf(provider(), n.changeNumber) }
            } ?: { n.changeNumber }
            val keys = fetchCoordinator.fetchedKeys().filter(keyFilter)
            val perKeyDelay = n.let(delayProvider)
            val fetcher = CdnBypassFetcher(
                fetchAction = { key, tcn ->
                    val evalKey = keys.first { it.key.matchingKey == key }
                    fetchCoordinator.fetchIfNeeded(evalKey, evaluationFilters, FetchReason.PUSH, targetChangeNumber = tcn)
                },
                freshnessChecker = { key, targetCn ->
                    val evalKey = keys.firstOrNull { it.key.matchingKey == key } ?: return@CdnBypassFetcher false
                    checker(evalKey) >= targetCn
                },
                backoffBaseMs = cdnBypassBackoffBaseMs,
                perKeyDelayProvider = perKeyDelay?.let { provider ->
                    { key -> keys.firstOrNull { it.key.matchingKey == key }?.let(provider) ?: 0L }
                },
            )
            fetcher.fetch(keys.map { it.key.matchingKey }, cnProvider)
        } else {
            fetchCoordinator.refetchAll(
                filters = evaluationFilters,
                reason = FetchReason.PUSH,
                delayProvider = n.let(delayProvider),
                keyFilter = keyFilter,
            )
        }
    }

    private suspend fun refetchAll(n: EvaluationUpdateNotification?) {
        fetchCoordinator.refetchAll(
            filters = evaluationFilters,
            reason = FetchReason.PUSH,
            delayProvider = n?.let(delayProvider),
            keyFilter = { true },
        )
    }
}
