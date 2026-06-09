package io.split.client.thin.internal

import io.split.android.client.streaming.support.CompressionType
import io.split.android.client.streaming.support.CompressionUtilProvider
import io.split.client.thin.Key
import io.split.client.thin.internal.evaluation.EvaluationFetchCoordinator
import io.split.client.thin.internal.secure.EvaluationFilters
import io.split.client.thin.internal.evaluation.EvaluationKey
import io.split.client.thin.internal.evaluation.FetchReason
import io.split.client.thin.internal.streaming.EvaluationPayloadDecoder
import io.split.client.thin.internal.streaming.EvaluationUpdateNotification
import io.split.client.thin.internal.streaming.EvaluationUpdateStrategy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationUpdateNotificationHandlerTest {

    private val key1 = EvaluationKey(Key("user-1"))
    private val key2 = EvaluationKey(Key("user-2"))

    private fun makeHandler(
        decodeResult: ByteArray = byteArrayOf(0xFF.toByte()),
        throwOnDecode: Throwable? = null,
        onPushHandlingError: (Throwable) -> Unit = {},
    ): Triple<EvaluationUpdateNotificationHandler, FakeHandlerCoordinator, FakeHandlerDecoder> {
        val coordinator = FakeHandlerCoordinator()
        val decoder = FakeHandlerDecoder(decodeResult = decodeResult, throwOnDecode = throwOnDecode)
        val handler = EvaluationUpdateNotificationHandler(
            decoder = decoder,
            fetchCoordinator = coordinator,
            delayProvider = { null },
            onPushHandlingError = onPushHandlingError,
        )
        return Triple(handler, coordinator, decoder)
    }

    private fun unboundedNotification() = EvaluationUpdateNotification(
        changeNumber = 1L, channelName = "ch", eventTimestamp = 1000L,
        updateStrategy = EvaluationUpdateStrategy.UNBOUNDED_FETCH_REQUEST,
        data = null, compression = CompressionType.NONE,
    )

    private fun boundedNotification(data: String? = "somePayload") = EvaluationUpdateNotification(
        changeNumber = 1L, channelName = "ch", eventTimestamp = 1000L,
        updateStrategy = EvaluationUpdateStrategy.BOUNDED_FETCH_REQUEST,
        data = data, compression = CompressionType.NONE,
    )

    // ── null notification → refetchAll(always-true) ───────────────────────────

    @Test
    fun `null notification triggers refetchAll`() = runTest {
        val (handler, coordinator, _) = makeHandler()
        handler.handle(null)
        assertEquals(1, coordinator.refetchAllCalls.size)
    }

    @Test
    fun `null notification keyFilter passes all keys`() = runTest {
        val (handler, coordinator, _) = makeHandler()
        handler.handle(null)
        val filter = coordinator.refetchAllCalls[0].keyFilter
        assertTrue(filter(key1))
        assertTrue(filter(key2))
    }

    // ── UNBOUNDED → refetchAll(always-true) ───────────────────────────────────

    @Test
    fun `UNBOUNDED notification triggers refetchAll`() = runTest {
        val (handler, coordinator, _) = makeHandler()
        handler.handle(unboundedNotification())
        assertEquals(1, coordinator.refetchAllCalls.size)
    }

    @Test
    fun `UNBOUNDED notification keyFilter passes all keys`() = runTest {
        val (handler, coordinator, _) = makeHandler()
        handler.handle(unboundedNotification())
        val filter = coordinator.refetchAllCalls[0].keyFilter
        assertTrue(filter(key1))
        assertTrue(filter(key2))
    }

    // ── null updateStrategy (unsupported codes 2/3) → fallback ────────────────

    @Test
    fun `null updateStrategy triggers refetchAll`() = runTest {
        val notification = EvaluationUpdateNotification(
            changeNumber = 1L, channelName = "ch", eventTimestamp = 1000L,
            updateStrategy = null, data = null, compression = CompressionType.NONE,
        )
        val (handler, coordinator, _) = makeHandler()
        handler.handle(notification)
        assertEquals(1, coordinator.refetchAllCalls.size)
        assertTrue(coordinator.refetchAllCalls[0].keyFilter(key1))
    }

    // ── BOUNDED + null data → fallback, no error callback ─────────────────────

    @Test
    fun `BOUNDED with null data falls back to refetchAll`() = runTest {
        val (handler, coordinator, _) = makeHandler()
        handler.handle(boundedNotification(data = null))
        assertEquals(1, coordinator.refetchAllCalls.size)
        assertTrue(coordinator.refetchAllCalls[0].keyFilter(key1))
    }

    @Test
    fun `BOUNDED with null data does not call onPushHandlingError`() = runTest {
        val errors = mutableListOf<Throwable>()
        val (handler, _, _) = makeHandler(onPushHandlingError = { errors.add(it) })
        handler.handle(boundedNotification(data = null))
        assertTrue(errors.isEmpty())
    }

    // ── BOUNDED + empty bitmap → fallback, no error callback ──────────────────

    @Test
    fun `BOUNDED with empty bitmap falls back to refetchAll`() = runTest {
        val (handler, coordinator, _) = makeHandler(decodeResult = byteArrayOf())
        handler.handle(boundedNotification())
        assertEquals(1, coordinator.refetchAllCalls.size)
        assertTrue(coordinator.refetchAllCalls[0].keyFilter(key1))
    }

    @Test
    fun `BOUNDED with empty bitmap does not call onPushHandlingError`() = runTest {
        val errors = mutableListOf<Throwable>()
        val (handler, _, _) = makeHandler(decodeResult = byteArrayOf(), onPushHandlingError = { errors.add(it) })
        handler.handle(boundedNotification())
        assertTrue(errors.isEmpty())
    }

    // ── BOUNDED + decoder throws → fallback + onPushHandlingError ─────────────

    @Test
    fun `BOUNDED where decoder throws falls back to refetchAll`() = runTest {
        val (handler, coordinator, _) = makeHandler(throwOnDecode = RuntimeException("boom"))
        handler.handle(boundedNotification())
        assertEquals(1, coordinator.refetchAllCalls.size)
        assertTrue(coordinator.refetchAllCalls[0].keyFilter(key1))
    }

    @Test
    fun `BOUNDED where decoder throws calls onPushHandlingError`() = runTest {
        val errors = mutableListOf<Throwable>()
        val (handler, _, _) = makeHandler(
            throwOnDecode = RuntimeException("boom"),
            onPushHandlingError = { errors.add(it) }
        )
        handler.handle(boundedNotification())
        assertEquals(1, errors.size)
    }

    // ── BOUNDED + valid bitmap → filtered refetchAll once ─────────────────────

    @Test
    fun `BOUNDED with valid bitmap calls refetchAll once`() = runTest {
        val (handler, coordinator, _) = makeHandler(decodeResult = byteArrayOf(0xFF.toByte()))
        handler.handle(boundedNotification())
        assertEquals(1, coordinator.refetchAllCalls.size)
    }

    // ── CancellationException must propagate ──────────────────────────────────

    @Test
    fun `CancellationException from coordinator propagates`() = runTest {
        val coordinator = object : FakeHandlerCoordinator() {
            override suspend fun refetchAll(
                filters: EvaluationFilters,
                reason: FetchReason,
                delayProvider: ((EvaluationKey) -> Long)?,
                keyFilter: (EvaluationKey) -> Boolean,
            ) { throw CancellationException("cancelled") }
        }
        val handler = EvaluationUpdateNotificationHandler(
            decoder = FakeHandlerDecoder(),
            fetchCoordinator = coordinator,
            delayProvider = { null },
        )
        var propagated = false
        try {
            handler.handle(unboundedNotification())
        } catch (e: CancellationException) {
            propagated = true
        }
        assertTrue(propagated)
    }

    // ── CDN bypass ────────────────────────────────────────────────────────────

    private fun makeHandlerWithBypass(
        knownKeys: Set<EvaluationKey> = emptySet(),
        changeNumberInStorage: Long = -1L,
    ): Pair<EvaluationUpdateNotificationHandler, FakeHandlerCoordinator> {
        val coordinator = FakeHandlerCoordinator(knownKeysResult = knownKeys)
        val handler = EvaluationUpdateNotificationHandler(
            decoder = FakeHandlerDecoder(),
            fetchCoordinator = coordinator,
            delayProvider = { null },
            freshnessChecker = { _ -> changeNumberInStorage },
        )
        return handler to coordinator
    }

    @Test
    fun `UNBOUNDED notification uses CDN bypass when changeNumber is present`() = runTest {
        val (handler, coordinator) = makeHandlerWithBypass(
            knownKeys = setOf(key1),
            changeNumberInStorage = -1L,
        )

        handler.handle(unboundedNotification())

        // bypass retries 10 times + 1 bypass = 11 calls to fetchIfNeeded
        assertEquals(11, coordinator.fetchIfNeededCalls.count { it.first == key1 })
    }

    @Test
    fun `CDN bypass uses targetChangeNumber on 11th attempt`() = runTest {
        val (handler, coordinator) = makeHandlerWithBypass(
            knownKeys = setOf(key1),
            changeNumberInStorage = -1L,
        )

        handler.handle(unboundedNotification()) // changeNumber = 1L

        val lastCall = coordinator.fetchIfNeededCalls.last { it.first == key1 }
        assertEquals(1L, lastCall.second)
    }

    @Test
    fun `targetChangeNumberProvider overrides notification changeNumber when higher`() = runTest {
        // Simulates newer notifications having advanced the shared change number to 99 while this
        // (changeNumber=1) fetch was in flight. The CDN bypass attempt must target 99, not 1.
        val coordinator = FakeHandlerCoordinator(knownKeysResult = setOf(key1))
        val handler = EvaluationUpdateNotificationHandler(
            decoder = FakeHandlerDecoder(),
            fetchCoordinator = coordinator,
            delayProvider = { null },
            freshnessChecker = { _ -> -1L }, // always stale
            targetChangeNumberProvider = { 99L },
        )

        handler.handle(unboundedNotification()) // notification changeNumber = 1L

        val lastCall = coordinator.fetchIfNeededCalls.last { it.first == key1 }
        assertEquals(99L, lastCall.second)
    }

    @Test
    fun `CDN bypass skips fetch when key is already fresh`() = runTest {
        val (handler, coordinator) = makeHandlerWithBypass(
            knownKeys = setOf(key1),
            changeNumberInStorage = 1L, // already at target changeNumber
        )

        handler.handle(unboundedNotification()) // changeNumber = 1L

        assertEquals(0, coordinator.fetchIfNeededCalls.count { it.first == key1 })
    }

    @Test
    fun `CDN bypass stops retrying once key becomes fresh`() = runTest {
        var callCount = 0
        val coordinator = object : FakeHandlerCoordinator(knownKeysResult = setOf(key1)) {
            override suspend fun fetchIfNeeded(evalKey: EvaluationKey, filters: EvaluationFilters, reason: FetchReason, delayMs: Long, targetChangeNumber: Long?): Boolean {
                callCount++
                return super.fetchIfNeeded(evalKey, filters, reason, delayMs, targetChangeNumber)
            }
        }
        var fetchCount = 0
        val handler = EvaluationUpdateNotificationHandler(
            decoder = FakeHandlerDecoder(),
            fetchCoordinator = coordinator,
            delayProvider = { null },
            freshnessChecker = { _ -> if (fetchCount++ >= 3) 1L else -1L },
        )

        handler.handle(unboundedNotification()) // changeNumber = 1L

        assertEquals(3, callCount)
    }

}

// ── Test fakes ─────────────────────────────────────────────────────────────

data class RefetchAllCall(
    val filters: EvaluationFilters,
    val reason: FetchReason,
    val keyFilter: (EvaluationKey) -> Boolean,
)

open class FakeHandlerCoordinator(
    private val knownKeysResult: Set<EvaluationKey> = emptySet(),
) : EvaluationFetchCoordinator {
    val refetchAllCalls = mutableListOf<RefetchAllCall>()
    val fetchIfNeededCalls = mutableListOf<Pair<EvaluationKey, Long?>>()

    override fun fetchedKeys(): Set<EvaluationKey> = knownKeysResult

    override suspend fun fetchIfNeeded(
        evalKey: EvaluationKey, filters: EvaluationFilters, reason: FetchReason, delayMs: Long, targetChangeNumber: Long?,
    ): Boolean {
        fetchIfNeededCalls.add(evalKey to targetChangeNumber)
        return true
    }

    override suspend fun refetchAll(
        filters: EvaluationFilters,
        reason: FetchReason,
        delayProvider: ((EvaluationKey) -> Long)?,
        keyFilter: (EvaluationKey) -> Boolean,
    ) { refetchAllCalls.add(RefetchAllCall(filters, reason, keyFilter)) }

    override fun forget(evalKey: EvaluationKey) {}
}

class FakeHandlerDecoder(
    private val decodeResult: ByteArray = byteArrayOf(0xFF.toByte()),
    private val throwOnDecode: Throwable? = null,
) : EvaluationPayloadDecoder(CompressionUtilProvider()) {
    override fun decodeAsBytes(data: String, compression: CompressionType): ByteArray {
        throwOnDecode?.let { throw it }
        return decodeResult
    }
}
