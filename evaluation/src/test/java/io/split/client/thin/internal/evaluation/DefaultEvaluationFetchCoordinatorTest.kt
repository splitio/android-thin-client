package io.split.client.thin.internal.evaluation

import io.split.client.thin.EvaluationResult
import io.split.client.thin.Key
import io.split.client.thin.internal.secure.EvaluationFilters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultEvaluationFetchCoordinatorTest {

    private val evalKey = EvaluationKey(Key("user-1"))
    private val evalKeyWithAttrs = EvaluationKey(Key("user-1"), mapOf("plan" to "premium"))

    private fun storedEval(flag: String) =
        StoredEvaluation(EvaluationResult(flag = flag, treatment = "on"))

    private fun makeCoordinator(
        changeToReturn: EvaluationChange = EvaluationChange(evalKey, changeNumber = 1L, evaluations = emptyList()),
        throwOnFetch: Throwable? = null,
    ): Triple<DefaultEvaluationFetchCoordinator, FakeEvaluationProvider, FakeEvaluationWriteStorage> {
        val provider = FakeEvaluationProvider(changeToReturn = changeToReturn, throwOnFetch = throwOnFetch)
        val writeStorage = FakeEvaluationWriteStorage()
        val coordinator = coordinatorWith(provider = provider, writeStorage = writeStorage)
        return Triple(coordinator, provider, writeStorage)
    }

    private fun coordinatorWith(
        provider: EvaluationProvider = FakeEvaluationProvider(),
        readStorage: EvaluationReadStorage = FakeEvaluationReadStorage(),
        writeStorage: EvaluationWriteStorage = FakeEvaluationWriteStorage(),
        onEvaluationsUpdated: (EvaluationKey, FetchReason, List<String>) -> Unit = { _, _, _ -> },
        onEvalFetchRequested: (EvaluationKey, FetchReason, Long) -> Unit = { _, _, _ -> },
        onEvalFetchSucceeded: (EvaluationKey) -> Unit = {},
        onEvalFetchFailed: (EvaluationKey, Throwable) -> Unit = { _, _ -> },
    ) = DefaultEvaluationFetchCoordinator(
        provider = provider,
        readStorage = readStorage,
        writeStorage = writeStorage,
        onEvaluationsUpdated = onEvaluationsUpdated,
        onEvalFetchRequested = onEvalFetchRequested,
        onEvalFetchSucceeded = onEvalFetchSucceeded,
        onEvalFetchFailed = onEvalFetchFailed,
    )

    @Test
    fun `fetchIfNeeded calls provider and writes to storage, returns true`() = runTest {
        val change = EvaluationChange(evalKey, changeNumber = 10L, evaluations = listOf(storedEval("flag-a")))
        val (coordinator, provider, writeStorage) = makeCoordinator(changeToReturn = change)

        val result = coordinator.fetchIfNeeded(evalKey, EvaluationFilters(), FetchReason.INITIALIZATION)

        assertTrue(result)
        assertEquals(1, provider.fetchCalls.size)
        assertEquals(1, writeStorage.upsertCalls.size)
        assertEquals(change, writeStorage.upsertCalls[0])
    }

    @Test
    fun `passes lastChangeNumber from storage as changeNumber to provider`() = runTest {
        val readStorage = FakeEvaluationReadStorage().apply { setChangeNumber(evalKey, 42L) }
        val provider = FakeEvaluationProvider()
        val coordinator = coordinatorWith(provider = provider, readStorage = readStorage)
        val filters = EvaluationFilters(sets = setOf("flag-a"))

        coordinator.fetchIfNeeded(evalKey, filters, FetchReason.PERIODIC)

        val call = provider.fetchCalls[0]
        assertEquals(42L, call.changeNumber)
        assertEquals(setOf("flag-a"), call.filters.sets)
    }

    @Test
    fun `fetchIfNeeded returns false on provider error without propagating`() = runTest {
        val error = RuntimeException("fetch failed")
        val (coordinator, _, _) = makeCoordinator(throwOnFetch = error)

        val result = coordinator.fetchIfNeeded(evalKey, EvaluationFilters(), FetchReason.INITIALIZATION)

        assertFalse(result)
    }

    @Test
    fun `fetchIfNeeded invokes onEvalFetchFailed on provider error`() = runTest {
        val error = RuntimeException("fetch failed")
        val capturedErrors = mutableListOf<Throwable>()
        val coordinator = coordinatorWith(
            provider = FakeEvaluationProvider(throwOnFetch = error),
            onEvalFetchFailed = { _, t -> capturedErrors.add(t) },
        )

        coordinator.fetchIfNeeded(evalKey, EvaluationFilters(), FetchReason.INITIALIZATION)

        assertEquals(listOf(error), capturedErrors)
    }

    @Test
    fun `fetchIfNeeded returns false and notifies failure on EvaluationAuthException`() = runTest {
        val error = EvaluationAuthException("evaluations unauthorized")
        val capturedErrors = mutableListOf<Throwable>()
        val coordinator = coordinatorWith(
            provider = FakeEvaluationProvider(throwOnFetch = error),
            onEvalFetchFailed = { _, t -> capturedErrors.add(t) },
        )

        val result = coordinator.fetchIfNeeded(evalKey, EvaluationFilters(), FetchReason.PERIODIC)

        assertFalse(result)
        assertEquals(listOf(error), capturedErrors)
    }

    @Test
    fun `null change on first fetch still triggers onEvaluationsUpdated`() = runTest {
        val capturedReasons = mutableListOf<FetchReason>()
        val coordinator = coordinatorWith(
            provider = FakeEvaluationProvider(returnNullChange = true),
            onEvaluationsUpdated = { _, reason, _ -> capturedReasons.add(reason) },
        )

        coordinator.fetchIfNeeded(evalKey, EvaluationFilters(), FetchReason.INITIALIZATION)

        assertEquals(listOf(FetchReason.INITIALIZATION), capturedReasons)
    }

    @Test
    fun `null change on subsequent fetch does not trigger onEvaluationsUpdated`() = runTest {
        val capturedReasons = mutableListOf<FetchReason>()
        val coordinator = coordinatorWith(
            provider = FakeEvaluationProvider(returnNullChange = true),
            onEvaluationsUpdated = { _, reason, _ -> capturedReasons.add(reason) },
        )

        coordinator.fetchIfNeeded(evalKey, EvaluationFilters(), FetchReason.INITIALIZATION)
        coordinator.fetchIfNeeded(evalKey, EvaluationFilters(), FetchReason.PERIODIC)

        assertEquals(1, capturedReasons.size)
        assertEquals(FetchReason.INITIALIZATION, capturedReasons[0])
    }

    @Test
    fun `different keys are fetched independently`() = runTest {
        val change1 = EvaluationChange(evalKey, changeNumber = 1L, evaluations = listOf(storedEval("flag-a")))
        val provider = FakeEvaluationProvider(changeToReturn = change1)
        val writeStorage = FakeEvaluationWriteStorage()
        val coordinator = coordinatorWith(provider = provider, writeStorage = writeStorage)

        coordinator.fetchIfNeeded(evalKey, EvaluationFilters(), FetchReason.INITIALIZATION)
        coordinator.fetchIfNeeded(evalKeyWithAttrs, EvaluationFilters(), FetchReason.INITIALIZATION)

        assertEquals(2, provider.fetchCalls.size)
        assertEquals(2, writeStorage.upsertCalls.size)
    }

    @Test
    fun `fetchIfNeeded invokes onEvaluationsUpdated with INITIALIZATION reason`() = runTest {
        val capturedReason = mutableListOf<FetchReason>()
        val coordinator = coordinatorWith(
            onEvaluationsUpdated = { _, reason, _ -> capturedReason.add(reason) },
        )

        coordinator.fetchIfNeeded(evalKey, EvaluationFilters(), FetchReason.INITIALIZATION)

        assertEquals(listOf(FetchReason.INITIALIZATION), capturedReason)
    }

    @Test
    fun `fetchIfNeeded invokes onEvaluationsUpdated on first fetch even when upsert reports no change`() = runTest {
        var callbackInvoked = false
        val coordinator = coordinatorWith(
            writeStorage = FakeEvaluationWriteStorage(upsertUpdated = false),
            onEvaluationsUpdated = { _, _, _ -> callbackInvoked = true },
        )

        coordinator.fetchIfNeeded(evalKey, EvaluationFilters(), FetchReason.INITIALIZATION)

        assertTrue(callbackInvoked)
    }

    @Test
    fun `fetchIfNeeded does not invoke onEvaluationsUpdated on subsequent fetch when upsert reports no change`() = runTest {
        val capturedReasons = mutableListOf<FetchReason>()
        val coordinator = coordinatorWith(
            writeStorage = FakeEvaluationWriteStorage(upsertUpdated = false),
            onEvaluationsUpdated = { _, reason, _ -> capturedReasons.add(reason) },
        )

        coordinator.fetchIfNeeded(evalKey, EvaluationFilters(), FetchReason.INITIALIZATION)
        coordinator.fetchIfNeeded(evalKey, EvaluationFilters(), FetchReason.PERIODIC)

        assertEquals(1, capturedReasons.size)
        assertEquals(FetchReason.INITIALIZATION, capturedReasons[0])
    }

    @Test
    fun `fetchIfNeeded does not invoke onEvaluationsUpdated on provider error`() = runTest {
        var callbackInvoked = false
        val coordinator = coordinatorWith(
            provider = FakeEvaluationProvider(throwOnFetch = RuntimeException("fetch failed")),
            onEvaluationsUpdated = { _, _, _ -> callbackInvoked = true },
        )

        coordinator.fetchIfNeeded(evalKey, EvaluationFilters(), FetchReason.INITIALIZATION)

        assertFalse(callbackInvoked)
    }

    @Test
    fun `refetchAll calls fetchIfNeeded for each key in fetchedKeys`() = runTest {
        val key1 = EvaluationKey(Key("user-1"))
        val key2 = EvaluationKey(Key("user-2"))
        val key3 = EvaluationKey(Key("user-3"))
        val (coordinator, provider, _) = makeCoordinator()

        // Populate fetchedKeys by fetching 3 keys
        coordinator.fetchIfNeeded(key1, EvaluationFilters(), FetchReason.INITIALIZATION)
        coordinator.fetchIfNeeded(key2, EvaluationFilters(), FetchReason.INITIALIZATION)
        coordinator.fetchIfNeeded(key3, EvaluationFilters(), FetchReason.INITIALIZATION)

        // Now refetch all
        coordinator.refetchAll(EvaluationFilters(), FetchReason.PERIODIC)

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
        coordinator.fetchIfNeeded(key1, EvaluationFilters(), FetchReason.INITIALIZATION)
        coordinator.fetchIfNeeded(key2, EvaluationFilters(), FetchReason.INITIALIZATION)

        val filters = EvaluationFilters(sets = setOf("flag-a"))
        coordinator.refetchAll(filters, FetchReason.PUSH)

        // Verify the refetch calls (last 2) have correct scope
        val refetchCalls = provider.fetchCalls.drop(2)
        assertEquals(2, refetchCalls.size)
        refetchCalls.forEach { call ->
            assertEquals(setOf("flag-a"), call.filters.sets)
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
            override suspend fun fetch(evalKey: EvaluationKey, filters: EvaluationFilters, changeNumber: Long, targetChangeNumber: Long?): EvaluationChange? {
                fetchCount++
                // Throw on the 5th call overall (2nd refetch)
                if (fetchCount == 5) throw RuntimeException("fetch failed")
                return EvaluationChange(evalKey, changeNumber = 1L, evaluations = emptyList())
            }
        }

        val coordinator = coordinatorWith(provider = provider)

        // Populate fetchedKeys (calls 1, 2, 3)
        coordinator.fetchIfNeeded(key1, EvaluationFilters(), FetchReason.INITIALIZATION)
        coordinator.fetchIfNeeded(key2, EvaluationFilters(), FetchReason.INITIALIZATION)
        coordinator.fetchIfNeeded(key3, EvaluationFilters(), FetchReason.INITIALIZATION)

        // Refetch all - should attempt all 3 despite error on 2nd (calls 4, 5, 6)
        coordinator.refetchAll(EvaluationFilters(), FetchReason.PERIODIC)

        // All 6 calls should have been attempted
        assertEquals(6, fetchCount)
    }

    @Test
    fun `refetchAll continues on EvaluationAuthException`() = runTest {
        val key1 = EvaluationKey(Key("user-1"))
        val key2 = EvaluationKey(Key("user-2"))
        val key3 = EvaluationKey(Key("user-3"))
        var fetchCount = 0
        val capturedErrors = mutableListOf<Throwable>()
        val provider = object : EvaluationProvider {
            override suspend fun fetch(evalKey: EvaluationKey, filters: EvaluationFilters, changeNumber: Long, targetChangeNumber: Long?): EvaluationChange? {
                fetchCount++
                if (fetchCount == 5) throw EvaluationAuthException("evaluations unauthorized")
                return EvaluationChange(evalKey, changeNumber = 1L, evaluations = emptyList())
            }
        }
        val coordinator = coordinatorWith(
            provider = provider,
            onEvalFetchFailed = { _, t -> capturedErrors.add(t) },
        )

        coordinator.fetchIfNeeded(key1, EvaluationFilters(), FetchReason.INITIALIZATION)
        coordinator.fetchIfNeeded(key2, EvaluationFilters(), FetchReason.INITIALIZATION)
        coordinator.fetchIfNeeded(key3, EvaluationFilters(), FetchReason.INITIALIZATION)
        coordinator.refetchAll(EvaluationFilters(), FetchReason.PERIODIC)

        assertEquals(6, fetchCount)
        assertEquals(1, capturedErrors.size)
        assertTrue(capturedErrors.single() is EvaluationAuthException)
    }

    @Test
    fun `refetchAll does nothing when fetchedKeys is empty`() = runTest {
        val (coordinator, provider, _) = makeCoordinator()

        coordinator.refetchAll(EvaluationFilters(), FetchReason.PERIODIC)

        assertEquals(0, provider.fetchCalls.size)
    }

    @Test
    fun `refetchAll invokes delayProvider for each key before fetching`() = runTest {
        val key1 = EvaluationKey(Key("user-1"))
        val key2 = EvaluationKey(Key("user-2"))
        val (coordinator, provider, _) = makeCoordinator()

        coordinator.fetchIfNeeded(key1, EvaluationFilters(), FetchReason.INITIALIZATION)
        coordinator.fetchIfNeeded(key2, EvaluationFilters(), FetchReason.INITIALIZATION)

        val delayedKeys = mutableListOf<EvaluationKey>()
        coordinator.refetchAll(EvaluationFilters(), FetchReason.PERIODIC, delayProvider = { key ->
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

        coordinator.fetchIfNeeded(key1, EvaluationFilters(), FetchReason.INITIALIZATION)
        coordinator.refetchAll(EvaluationFilters(), FetchReason.PERIODIC, delayProvider = null)

        assertEquals(2, provider.fetchCalls.size)
    }

    @Test
    fun `CancellationException from provider propagates out of fetchIfNeeded`() = runTest {
        val coordinator = coordinatorWith(
            provider = FakeEvaluationProvider(throwOnFetch = CancellationException("cancelled")),
        )

        var propagated = false
        try {
            coordinator.fetchIfNeeded(evalKey, EvaluationFilters(), FetchReason.INITIALIZATION)
        } catch (e: CancellationException) {
            propagated = true
        }

        assertTrue("CancellationException must not be swallowed", propagated)
    }

    @Test
    fun `onEvalFetchRequested receives delayMs from refetchAll delayProvider`() = runTest {
        val key1 = EvaluationKey(Key("user-1"))
        val capturedDelays = mutableListOf<Long>()
        val coordinator = coordinatorWith(
            onEvalFetchRequested = { _, _, delayMs -> capturedDelays.add(delayMs) },
        )

        coordinator.fetchIfNeeded(key1, EvaluationFilters(), FetchReason.INITIALIZATION)
        coordinator.refetchAll(EvaluationFilters(), FetchReason.PUSH, delayProvider = { 500L })

        assertEquals(2, capturedDelays.size)
        assertEquals(0L, capturedDelays[0])
        assertEquals(500L, capturedDelays[1])
    }

    @Test
    fun `onEvalFetchRequested receives zero delayMs when called via direct fetchIfNeeded`() = runTest {
        val capturedDelays = mutableListOf<Long>()
        val coordinator = coordinatorWith(
            onEvalFetchRequested = { _, _, delayMs -> capturedDelays.add(delayMs) },
        )

        coordinator.fetchIfNeeded(evalKey, EvaluationFilters(), FetchReason.INITIALIZATION)

        assertEquals(listOf(0L), capturedDelays)
    }

    @Test
    fun `refetchAll with keyFilter only fetches keys passing the filter`() = runTest {
        val key1 = EvaluationKey(Key("user-1"))
        val key2 = EvaluationKey(Key("user-2"))
        val key3 = EvaluationKey(Key("user-3"))
        val (coordinator, provider, _) = makeCoordinator()

        coordinator.fetchIfNeeded(key1, EvaluationFilters(), FetchReason.INITIALIZATION)
        coordinator.fetchIfNeeded(key2, EvaluationFilters(), FetchReason.INITIALIZATION)
        coordinator.fetchIfNeeded(key3, EvaluationFilters(), FetchReason.INITIALIZATION)

        coordinator.refetchAll(EvaluationFilters(), FetchReason.PUSH, keyFilter = { it == key1 || it == key3 })

        val refetchedKeys = provider.fetchCalls.drop(3).map { it.evalKey }
        assertEquals(2, refetchedKeys.size)
        assertTrue(refetchedKeys.contains(key1))
        assertTrue(refetchedKeys.contains(key3))
        assertFalse(refetchedKeys.contains(key2))
    }

    @Test
    fun `refetchAll with always-false keyFilter fetches nothing`() = runTest {
        val key1 = EvaluationKey(Key("user-1"))
        val (coordinator, provider, _) = makeCoordinator()

        coordinator.fetchIfNeeded(key1, EvaluationFilters(), FetchReason.INITIALIZATION)
        coordinator.refetchAll(EvaluationFilters(), FetchReason.PUSH, keyFilter = { false })

        assertEquals(1, provider.fetchCalls.size)
    }

    @Test
    fun `refetchAll without keyFilter fetches all keys (default behaviour unchanged)`() = runTest {
        val key1 = EvaluationKey(Key("user-1"))
        val key2 = EvaluationKey(Key("user-2"))
        val (coordinator, provider, _) = makeCoordinator()

        coordinator.fetchIfNeeded(key1, EvaluationFilters(), FetchReason.INITIALIZATION)
        coordinator.fetchIfNeeded(key2, EvaluationFilters(), FetchReason.INITIALIZATION)
        coordinator.refetchAll(EvaluationFilters(), FetchReason.PUSH)

        assertEquals(4, provider.fetchCalls.size)
    }

    @Test
    fun `fetchIfNeeded skips upsert when changeNumber matches storage and evaluations is empty`() = runTest {
        val readStorage = FakeEvaluationReadStorage().apply { setChangeNumber(evalKey, 42L) }
        val provider = FakeEvaluationProvider(
            changeToReturn = EvaluationChange(evalKey, changeNumber = 42L, evaluations = emptyList())
        )
        val writeStorage = FakeEvaluationWriteStorage()
        val capturedReasons = mutableListOf<FetchReason>()
        var succeeded = false
        val coordinator = coordinatorWith(
            provider = provider,
            readStorage = readStorage,
            writeStorage = writeStorage,
            onEvaluationsUpdated = { _, reason, _ -> capturedReasons.add(reason) },
            onEvalFetchSucceeded = { succeeded = true },
        )

        val result = coordinator.fetchIfNeeded(evalKey, EvaluationFilters(), FetchReason.PERIODIC)

        assertTrue(result)
        assertTrue(succeeded)
        assertEquals(0, writeStorage.upsertCalls.size)
        assertEquals(listOf(FetchReason.PERIODIC), capturedReasons)
    }

    @Test
    fun `fetchIfNeeded does NOT skip upsert when changeNumber matches but evaluations is non-empty`() = runTest {
        val readStorage = FakeEvaluationReadStorage().apply { setChangeNumber(evalKey, 42L) }
        val change = EvaluationChange(evalKey, changeNumber = 42L, evaluations = listOf(storedEval("flag-a")))
        val provider = FakeEvaluationProvider(changeToReturn = change)
        val writeStorage = FakeEvaluationWriteStorage()
        val coordinator = coordinatorWith(
            provider = provider,
            readStorage = readStorage,
            writeStorage = writeStorage,
        )

        coordinator.fetchIfNeeded(evalKey, EvaluationFilters(), FetchReason.PERIODIC)

        assertEquals(1, writeStorage.upsertCalls.size)
        assertEquals(change, writeStorage.upsertCalls[0])
    }

    @Test
    fun `fetchIfNeeded does NOT skip upsert when changeNumber differs and evaluations is empty`() = runTest {
        val readStorage = FakeEvaluationReadStorage().apply { setChangeNumber(evalKey, 41L) }
        val change = EvaluationChange(evalKey, changeNumber = 42L, evaluations = emptyList())
        val provider = FakeEvaluationProvider(changeToReturn = change)
        val writeStorage = FakeEvaluationWriteStorage()
        val coordinator = coordinatorWith(
            provider = provider,
            readStorage = readStorage,
            writeStorage = writeStorage,
        )

        coordinator.fetchIfNeeded(evalKey, EvaluationFilters(), FetchReason.PERIODIC)

        assertEquals(1, writeStorage.upsertCalls.size)
    }

    @Test
    fun `targetChangeNumber is forwarded to provider in fetchIfNeeded`() = runTest {
        val provider = FakeEvaluationProvider(
            changeToReturn = EvaluationChange(evalKey, changeNumber = 1L, evaluations = emptyList())
        )
        val coordinator = coordinatorWith(provider = provider)

        coordinator.fetchIfNeeded(evalKey, EvaluationFilters(), FetchReason.PUSH, targetChangeNumber = 42L)

        assertEquals(42L, provider.fetchCalls.first().targetChangeNumber)
    }

    @Test
    fun `targetChangeNumber defaults to null in fetchIfNeeded when not provided`() = runTest {
        val provider = FakeEvaluationProvider(
            changeToReturn = EvaluationChange(evalKey, changeNumber = 1L, evaluations = emptyList())
        )
        val coordinator = coordinatorWith(provider = provider)

        coordinator.fetchIfNeeded(evalKey, EvaluationFilters(), FetchReason.PUSH)

        assertEquals(null, provider.fetchCalls.first().targetChangeNumber)
    }

    @Test
    fun `forget removes key from fetchedKeys and subsequent refetchAll excludes it`() = runTest {
        val keyA = EvaluationKey(Key("user-a"))
        val keyB = EvaluationKey(Key("user-b"))
        val (coordinator, provider, _) = makeCoordinator()

        // Populate fetchedKeys with A and B
        coordinator.fetchIfNeeded(keyA, EvaluationFilters(), FetchReason.INITIALIZATION)
        coordinator.fetchIfNeeded(keyB, EvaluationFilters(), FetchReason.INITIALIZATION)

        assertEquals(setOf(keyA, keyB), coordinator.fetchedKeys())

        // Forget A
        coordinator.forget(keyA)

        assertEquals(setOf(keyB), coordinator.fetchedKeys())

        // refetchAll should only refetch B
        coordinator.refetchAll(EvaluationFilters(), FetchReason.PERIODIC)

        // 2 initial fetches + 1 refetch for keyB (keyA is forgotten)
        assertEquals(3, provider.fetchCalls.size)
        assertEquals(keyB, provider.fetchCalls[2].evalKey)
    }
}
