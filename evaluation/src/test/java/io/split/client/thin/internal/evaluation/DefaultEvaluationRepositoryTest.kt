package io.split.client.thin.internal.evaluation

import io.split.client.thin.EvaluationResult
import io.split.client.thin.Key
import io.split.client.thin.Target
import io.split.client.thin.internal.secure.EvaluationFilters
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultEvaluationRepositoryTest {

    private val evalKey = EvaluationKey(Key("user-1"))
    private val target = Target(Key("user-1"))

    private fun storedEval(flag: String, treatment: String, sets: Set<String> = emptySet()) =
        StoredEvaluation(EvaluationResult(flag = flag, treatment = treatment), flagSets = sets)

    private fun makeRepository(
        readStorage: FakeEvaluationReadStorage = FakeEvaluationReadStorage(),
        coordinator: FakeEvaluationFetchCoordinator = FakeEvaluationFetchCoordinator(),
    ): DefaultEvaluationRepository {
        return DefaultEvaluationRepository(
            readStorage = readStorage,
            fetchCoordinator = coordinator,
        )
    }

    @Test
    fun `getTreatment reads from storage`() = runTest {
        val readStorage = FakeEvaluationReadStorage()
        readStorage.store("flag-a", evalKey, storedEval("flag-a", "on"))
        val repo = makeRepository(readStorage = readStorage)

        val result = repo.getTreatment(evalKey, "flag-a")

        assertEquals(storedEval("flag-a", "on"), result)
    }

    @Test
    fun `getTreatment returns null for unknown flag`() = runTest {
        val repo = makeRepository()
        assertNull(repo.getTreatment(evalKey, "unknown"))
    }

    @Test
    fun `getTreatments reads multiple flags from storage`() = runTest {
        val readStorage = FakeEvaluationReadStorage()
        readStorage.store("flag-a", evalKey, storedEval("flag-a", "on"))
        readStorage.store("flag-b", evalKey, storedEval("flag-b", "off"))
        val repo = makeRepository(readStorage = readStorage)

        val result = repo.getTreatments(evalKey, setOf("flag-a", "flag-b"))

        assertEquals(2, result.size)
        assertEquals("on", result["flag-a"]?.result?.treatment)
        assertEquals("off", result["flag-b"]?.result?.treatment)
    }

    @Test
    fun `getTreatmentsByFlagSets delegates to storage`() = runTest {
        val readStorage = FakeEvaluationReadStorage()
        readStorage.store("flag-a", evalKey, storedEval("flag-a", "on", setOf("set1")))
        val repo = makeRepository(readStorage = readStorage)

        val result = repo.getTreatmentsByFlagSets(evalKey, setOf("set1"))

        assertEquals(1, result.size)
        assertEquals("on", result["flag-a"]?.result?.treatment)
    }

    @Test
    fun `setTarget triggers fetch coordinator with TARGET_SWITCH`() = runTest {
        val coordinator = FakeEvaluationFetchCoordinator()
        val repo = makeRepository(coordinator = coordinator)

        repo.setTarget(target, null)

        assertEquals(1, coordinator.fetchCalls.size)
        assertEquals(FetchReason.TARGET_SWITCH, coordinator.fetchCalls[0].third)
        assertEquals(evalKey, coordinator.fetchCalls[0].first)
    }

    @Test
    fun `setTarget passes filters to coordinator`() = runTest {
        val coordinator = FakeEvaluationFetchCoordinator()
        val repo = makeRepository(coordinator = coordinator)
        val filters = EvaluationFilters(flagNames = setOf("flag-a"), flagSets = null)

        repo.setTarget(target, filters)

        assertEquals(filters, coordinator.fetchCalls[0].second)
    }

    @Test
    fun `read methods await pending fetch before returning`() = runTest {
        val coordinator = FakeEvaluationFetchCoordinator()
        val repo = makeRepository(coordinator = coordinator)

        repo.getTreatment(evalKey, "flag-a")

        assertEquals(1, coordinator.awaitCalls.size)
        assertEquals(evalKey, coordinator.awaitCalls[0])
    }

    @Test
    fun `getTreatments awaits pending before returning`() = runTest {
        val coordinator = FakeEvaluationFetchCoordinator()
        val repo = makeRepository(coordinator = coordinator)

        repo.getTreatments(evalKey, setOf("flag-a"))

        assertEquals(1, coordinator.awaitCalls.size)
    }

    @Test
    fun `getTreatmentsByFlagSets awaits pending before returning`() = runTest {
        val coordinator = FakeEvaluationFetchCoordinator()
        val repo = makeRepository(coordinator = coordinator)

        repo.getTreatmentsByFlagSets(evalKey, setOf("set1"))

        assertEquals(1, coordinator.awaitCalls.size)
    }
}
