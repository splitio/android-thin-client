package io.split.client.thin.internal.evaluation

import io.split.client.thin.EvaluationResult
import io.split.client.thin.Key
import io.split.client.thin.internal.secure.EvaluationFilters
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
    fun `includes lastChangeNumber from storage in filters`() = runTest {
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

        val passedFilters = provider.fetchCalls[0].second
        assertEquals(42L, passedFilters?.changeNumber)
        assertEquals(setOf("flag-a"), passedFilters?.flagNames)
    }

    @Test
    fun `error propagates from fetchIfNeeded`() = runTest {
        val error = RuntimeException("fetch failed")
        val (coordinator, _, _) = makeCoordinator(throwOnFetch = error)

        var caughtError: Throwable? = null
        try {
            coordinator.fetchIfNeeded(evalKey, null, FetchReason.INITIALIZATION)
        } catch (e: RuntimeException) {
            caughtError = e
        }

        assertEquals(error, caughtError)
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
            onEvaluationsUpdated = { capturedReason.add(it) },
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
            onEvaluationsUpdated = { callbackInvoked = true },
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
            onEvaluationsUpdated = { capturedReasons.add(it) },
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
            onEvaluationsUpdated = { callbackInvoked = true },
        )

        var caughtError: Throwable? = null
        try {
            coordinator.fetchIfNeeded(evalKey, null, FetchReason.INITIALIZATION)
        } catch (e: RuntimeException) {
            caughtError = e
        }

        assertNotNull(caughtError)
        assertFalse(callbackInvoked)
    }
}
