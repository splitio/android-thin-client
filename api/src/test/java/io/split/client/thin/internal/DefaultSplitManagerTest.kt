package io.split.client.thin.internal

import io.split.client.thin.Key
import io.split.client.thin.Target
import io.split.client.thin.internal.evaluation.EvaluationKey
import io.split.client.thin.internal.evaluation.EvaluationRepository
import io.split.client.thin.internal.evaluation.StoredEvaluation
import io.split.client.thin.internal.secure.EvaluationFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSplitManagerTest {

    @Test
    fun `flagNames returns names from repository`() {
        val repo = FakeManagerRepository(setOf("flag-a", "flag-b"))
        val manager = DefaultSplitManager(repo)

        assertEquals(listOf("flag-a", "flag-b").sorted(), manager.flagNames.sorted())
    }

    @Test
    fun `flagNames returns results even after old eval key is cleared`() {
        val repo = FakeManagerRepository(setOf("flag-a"))
        val manager = DefaultSplitManager(repo)

        assertEquals(listOf("flag-a"), manager.flagNames)
    }

    @Test
    fun `flagNames returns empty when no evaluations stored`() {
        val repo = FakeManagerRepository(emptySet())
        val manager = DefaultSplitManager(repo)

        assertTrue(manager.flagNames.isEmpty())
    }

    private class FakeManagerRepository(private val names: Set<String>) : EvaluationRepository {
        override fun getFlagNames(): Set<String> = names
        override fun getFlagNames(evalKey: EvaluationKey): Set<String> = names
        override fun getTreatment(evalKey: EvaluationKey, flag: String): StoredEvaluation? = null
        override fun getTreatments(evalKey: EvaluationKey, flags: Set<String>): Map<String, StoredEvaluation> = emptyMap()
        override fun getTreatmentsByFlagSets(evalKey: EvaluationKey, flagSets: Set<String>): Map<String, StoredEvaluation> = emptyMap()
        override suspend fun setTarget(target: Target, filters: EvaluationFilters, isInitialization: Boolean) {}
    }
}
