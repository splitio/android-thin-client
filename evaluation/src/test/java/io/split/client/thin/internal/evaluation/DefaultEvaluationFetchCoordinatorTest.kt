package io.split.client.thin.internal.evaluation

import io.split.client.thin.EvaluationResult
import io.split.client.thin.Key
import io.split.client.thin.internal.secure.EvaluationFilters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultEvaluationFetchCoordinatorTest {

    private val evalKey = EvaluationKey(Key("user-1"))
    private val evalKeyWithAttrs = EvaluationKey(Key("user-1"), mapOf("plan" to "premium"))

    private fun storedEval(flag: String) =
        StoredEvaluation(EvaluationResult(flag = flag, treatment = "on"))

    private fun makeCoordinator(
        changeToReturn: EvaluationChange = EvaluationChange(evalKey, 1L, emptyList()),
        throwOnFetch: Throwable? = null,
    ): Triple<DefaultEvaluationFetchCoordinator, FakeEvaluationProvider, FakeEvaluationWriteStorage> {
        val provider = FakeEvaluationProvider(changeToReturn = changeToReturn, throwOnFetch = throwOnFetch)
        val readStorage = FakeEvaluationReadStorage()
        val writeStorage = FakeEvaluationWriteStorage()
        val coordinator = DefaultEvaluationFetchCoordinator(
            provider = provider,
            readStorage = readStorage,
            writeStorage = writeStorage,
        )
        return Triple(coordinator, provider, writeStorage)
    }

    @Test
    fun `fetchIfNeeded calls provider and writes to storage, returns true`() = runTest {
        val change = EvaluationChange(evalKey, 10L, listOf(storedEval("flag-a")))
        val (coordinator, provider, writeStorage) = makeCoordinator(changeToReturn = change)

        val result = coordinator.fetchIfNeeded(evalKey, null, FetchReason.INITIALIZATION)

        assertTrue(result)
        assertEquals(1, provider.fetchCalls.size)
        assertEquals(1, writeStorage.upsertCalls.size)
        assertEquals(change, writeStorage.upsertCalls[0])
    }

    @Test
    fun `passes lastChangeNumber from storage as changeNumber to provider`() = runTest {
        val readStorage = FakeEvaluationReadStorage()
        readStorage.setChangeNumber(evalKey, 42L)
        val provider = FakeEvaluationProvider()
        val writeStorage = FakeEvaluationWriteStorage()
        val coordinator = DefaultEvaluationFetchCoordinator(
            provider = provider,
            readStorage = readStorage,
            writeStorage = writeStorage,
        )
        val filters = EvaluationFilters(flagNames = setOf("flag-a"), flagSets = null)

        coordinator.fetchIfNeeded(evalKey, filters, FetchReason.PERIODIC)

        val call = provider.fetchCalls[0]
        assertEquals(42L, call.changeNumber)
        assertEquals(setOf("flag-a"), call.filters?.flagNames)
    }

    @Test
    fun `fetchIfNeeded returns false on provider error without propagating`() = runTest {
        val error = RuntimeException("fetch failed")
        val (coordinator, _, _) = makeCoordinator(throwOnFetch = error)

        val result = coordinator.fetchIfNeeded(evalKey, null, FetchReason.INITIALIZATION)

        assertFalse(result)
    }

    @Test
    fun `fetchIfNeeded invokes onEvalFetchFailed on provider error`() = runTest {
        val error = RuntimeException("fetch failed")
        val capturedErrors = mutableListOf<Throwable>()
        val coordinator = DefaultEvaluationFetchCoordinator(
            provider = FakeEvaluationProvider(throwOnFetch = error),
            readStorage = FakeEvaluationReadStorage(),
            writeStorage = FakeEvaluationWriteStorage(),
            onEvalFetchFailed = { _, t -> capturedErrors.add(t) },
        )

        coordinator.fetchIfNeeded(evalKey, null, FetchReason.INITIALIZATION)

        assertEquals(listOf(error), capturedErrors)
    }

    @Test
    fun `null change on first fetch still triggers onEvaluationsUpdated`() = runTest {
        val capturedReasons = mutableListOf<FetchReason>()
        val coordinator = DefaultEvaluationFetchCoordinator(
            provider = FakeEvaluationProvider(returnNullChange = true),
            readStorage = FakeEvaluationReadStorage(),
            writeStorage = FakeEvaluationWriteStorage(),
            onEvaluationsUpdated = { _, reason -> capturedReasons.add(reason) },
        )

        coordinator.fetchIfNeeded(evalKey, null, FetchReason.INITIALIZATION)

        assertEquals(listOf(FetchReason.INITIALIZATION), capturedReasons)
    }

    @Test
    fun `null change on subsequent fetch does not trigger onEvaluationsUpdated`() = runTest {
        val capturedReasons = mutableListOf<FetchReason>()
        val coordinator = DefaultEvaluationFetchCoordinator(
            provider = FakeEvaluationProvider(returnNullChange = true),
            readStorage = FakeEvaluationReadStorage(),
            writeStorage = FakeEvaluationWriteStorage(),
            onEvaluationsUpdated = { _, reason -> capturedReasons.add(reason) },
        )

        coordinator.fetchIfNeeded(evalKey, null, FetchReason.INITIALIZATION)
        coordinator.fetchIfNeeded(evalKey, null, FetchReason.PERIODIC)

        assertEquals(1, capturedReasons.size)
        assertEquals(FetchReason.INITIALIZATION, capturedReasons[0])
    }

    @Test
    fun `different keys are fetched independently`() = runTest {
        val change1 = EvaluationChange(evalKey, 1L, listOf(storedEval("flag-a")))
        val change2 = EvaluationChange(evalKeyWithAttrs, 2L, listOf(storedEval("flag-b")))
        val provider = FakeEvaluationProvider(changeToReturn = change1)
        val readStorage = FakeEvaluationReadStorage()
        val writeStorage = FakeEvaluationWriteStorage()
        val coordinator = DefaultEvaluationFetchCoordinator(provider, readStorage, writeStorage)

        coordinator.fetchIfNeeded(evalKey, null, FetchReason.INITIALIZATION)
        coordinator.fetchIfNeeded(evalKeyWithAttrs, null, FetchReason.INITIALIZATION)

        assertEquals(2, provider.fetchCalls.size)
        assertEquals(2, writeStorage.upsertCalls.size)
    }

    @Test
    fun `fetchIfNeeded invokes onEvaluationsUpdated with INITIALIZATION reason`() = runTest {
        val capturedReason = mutableListOf<FetchReason>()
        val coordinator = DefaultEvaluationFetchCoordinator(
            provider = FakeEvaluationProvider(),
            readStorage = FakeEvaluationReadStorage(),
            writeStorage = FakeEvaluationWriteStorage(),
            onEvaluationsUpdated = { _, reason -> capturedReason.add(reason) },
        )

        coordinator.fetchIfNeeded(evalKey, null, FetchReason.INITIALIZATION)

        assertEquals(listOf(FetchReason.INITIALIZATION), capturedReason)
    }

    @Test
    fun `fetchIfNeeded invokes onEvaluationsUpdated on first fetch even when upsert reports no change`() = runTest {
        var callbackInvoked = false
        val coordinator = DefaultEvaluationFetchCoordinator(
            provider = FakeEvaluationProvider(),
            readStorage = FakeEvaluationReadStorage(),
            writeStorage = FakeEvaluationWriteStorage(upsertResult = false),
            onEvaluationsUpdated = { _, _ -> callbackInvoked = true },
        )

        coordinator.fetchIfNeeded(evalKey, null, FetchReason.INITIALIZATION)

        assertTrue(callbackInvoked)
    }

    @Test
    fun `fetchIfNeeded does not invoke onEvaluationsUpdated on subsequent fetch when upsert reports no change`() = runTest {
        val capturedReasons = mutableListOf<FetchReason>()
        val coordinator = DefaultEvaluationFetchCoordinator(
            provider = FakeEvaluationProvider(),
            readStorage = FakeEvaluationReadStorage(),
            writeStorage = FakeEvaluationWriteStorage(upsertResult = false),
            onEvaluationsUpdated = { _, reason -> capturedReasons.add(reason) },
        )

        coordinator.fetchIfNeeded(evalKey, null, FetchReason.INITIALIZATION)
        coordinator.fetchIfNeeded(evalKey, null, FetchReason.PERIODIC)

        assertEquals(1, capturedReasons.size)
        assertEquals(FetchReason.INITIALIZATION, capturedReasons[0])
    }

    @Test
    fun `fetchIfNeeded does not invoke onEvaluationsUpdated on provider error`() = runTest {
        var callbackInvoked = false
        val coordinator = DefaultEvaluationFetchCoordinator(
            provider = FakeEvaluationProvider(throwOnFetch = RuntimeException("fetch failed")),
            readStorage = FakeEvaluationReadStorage(),
            writeStorage = FakeEvaluationWriteStorage(),
            onEvaluationsUpdated = { _, _ -> callbackInvoked = true },
        )

        coordinator.fetchIfNeeded(evalKey, null, FetchReason.INITIALIZATION)

        assertFalse(callbackInvoked)
    }

    @Test
    fun `refetchAll calls fetchIfNeeded for each key in fetchedKeys`() = runTest {
        val key1 = EvaluationKey(Key("user-1"))
        val key2 = EvaluationKey(Key("user-2"))
        val key3 = EvaluationKey(Key("user-3"))
        val (coordinator, provider, _) = makeCoordinator()

        // Populate fetchedKeys by fetching 3 keys
        coordinator.fetchIfNeeded(key1, null, FetchReason.INITIALIZATION)
        coordinator.fetchIfNeeded(key2, null, FetchReason.INITIALIZATION)
        coordinator.fetchIfNeeded(key3, null, FetchReason.INITIALIZATION)

        // Now refetch all
        coordinator.refetchAll(null, FetchReason.PERIODIC)

        // Should have 6 total calls: 3 initial + 3 from refetchAll
        assertEquals(6, provider.fetchCalls.size)
        // Verify the last 3 calls were with PERIODIC reason
        val refetchCalls = provider.fetchCalls.drop(3)
        assertTrue(refetchCalls.any { it.evalKey == key1 })
        assertTrue(refetchCalls.any { it.evalKey == key2 })
        assertTrue(refetchCalls.any { it.evalKey == key3 })
    }

    @Test
    fun `refetchAll passes filters and reason to each fetch`() = runTest {
        val key1 = EvaluationKey(Key("user-1"))
        val key2 = EvaluationKey(Key("user-2"))
        val (coordinator, provider, _) = makeCoordinator()

        // Populate fetchedKeys
        coordinator.fetchIfNeeded(key1, null, FetchReason.INITIALIZATION)
        coordinator.fetchIfNeeded(key2, null, FetchReason.INITIALIZATION)

        val filters = EvaluationFilters(flagNames = setOf("flag-a"), flagSets = null)
        coordinator.refetchAll(filters, FetchReason.PUSH)

        // Verify the refetch calls (last 2) have correct scope
        val refetchCalls = provider.fetchCalls.drop(2)
        assertEquals(2, refetchCalls.size)
        refetchCalls.forEach { call ->
            assertEquals(setOf("flag-a"), call.filters?.flagNames)
        }
    }

    @Test
    fun `refetchAll continues on error`() = runTest {
        val key1 = EvaluationKey(Key("user-1"))
        val key2 = EvaluationKey(Key("user-2"))
        val key3 = EvaluationKey(Key("user-3"))

        // Create a provider that throws on the second refetch call
        var fetchCount = 0
        val provider = object : EvaluationProvider {
            override suspend fun fetch(evalKey: EvaluationKey, filters: EvaluationFilters?, changeNumber: Long): EvaluationChange? {
                fetchCount++
                // Throw on the 5th call overall (2nd refetch)
                if (fetchCount == 5) throw RuntimeException("fetch failed")
                return EvaluationChange(evalKey, 1L, emptyList())
            }
        }

        val coordinator = DefaultEvaluationFetchCoordinator(
            provider = provider,
            readStorage = FakeEvaluationReadStorage(),
            writeStorage = FakeEvaluationWriteStorage(),
        )

        // Populate fetchedKeys (calls 1, 2, 3)
        coordinator.fetchIfNeeded(key1, null, FetchReason.INITIALIZATION)
        coordinator.fetchIfNeeded(key2, null, FetchReason.INITIALIZATION)
        coordinator.fetchIfNeeded(key3, null, FetchReason.INITIALIZATION)

        // Refetch all - should attempt all 3 despite error on 2nd (calls 4, 5, 6)
        coordinator.refetchAll(null, FetchReason.PERIODIC)

        // All 6 calls should have been attempted
        assertEquals(6, fetchCount)
    }

    @Test
    fun `refetchAll does nothing when fetchedKeys is empty`() = runTest {
        val (coordinator, provider, _) = makeCoordinator()

        coordinator.refetchAll(null, FetchReason.PERIODIC)

        assertEquals(0, provider.fetchCalls.size)
    }

    @Test
    fun `refetchAll invokes delayProvider for each key before fetching`() = runTest {
        val key1 = EvaluationKey(Key("user-1"))
        val key2 = EvaluationKey(Key("user-2"))
        val (coordinator, provider, _) = makeCoordinator()

        coordinator.fetchIfNeeded(key1, null, FetchReason.INITIALIZATION)
        coordinator.fetchIfNeeded(key2, null, FetchReason.INITIALIZATION)

        val delayedKeys = mutableListOf<EvaluationKey>()
        coordinator.refetchAll(null, FetchReason.PERIODIC, delayProvider = { key ->
            delayedKeys.add(key)
            0L
        })

        assertEquals(2, delayedKeys.size)
        assertTrue(delayedKeys.contains(key1))
        assertTrue(delayedKeys.contains(key2))
    }

    @Test
    fun `refetchAll without delayProvider still fetches all keys`() = runTest {
        val key1 = EvaluationKey(Key("user-1"))
        val (coordinator, provider, _) = makeCoordinator()

        coordinator.fetchIfNeeded(key1, null, FetchReason.INITIALIZATION)
        coordinator.refetchAll(null, FetchReason.PERIODIC, delayProvider = null)

        assertEquals(2, provider.fetchCalls.size)
    }

    @Test
    fun `CancellationException from provider propagates out of fetchIfNeeded`() = runTest {
        val provider = FakeEvaluationProvider(throwOnFetch = CancellationException("cancelled"))
        val coordinator = DefaultEvaluationFetchCoordinator(
            provider = provider,
            readStorage = FakeEvaluationReadStorage(),
            writeStorage = FakeEvaluationWriteStorage(),
        )

        var propagated = false
        try {
            coordinator.fetchIfNeeded(evalKey, null, FetchReason.INITIALIZATION)
        } catch (e: CancellationException) {
            propagated = true
        }

        assertTrue("CancellationException must not be swallowed", propagated)
    }

}
