package io.split.client.thin.internal.evaluation

import io.split.client.thin.EvaluationResult
import io.split.client.thin.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class InMemoryEvaluationStorageTest {

    private lateinit var storage: InMemoryEvaluationStorage

    private val key1 = EvaluationKey(Key("user-1"))
    private val key2 = EvaluationKey(Key("user-2"))
    private val keyWithAttrs = EvaluationKey(Key("user-1"), mapOf("plan" to "premium"))

    private fun storedEval(flag: String, treatment: String, sets: Set<String> = emptySet()) =
        StoredEvaluation(EvaluationResult(flag = flag, treatment = treatment), flagSets = sets)

    private fun change(
        evalKey: EvaluationKey,
        changeNumber: Long,
        vararg evals: StoredEvaluation,
    ) = EvaluationChange(evalKey, changeNumber, evals.toList())

    @Before
    fun setUp() {
        storage = InMemoryEvaluationStorage()
    }

    @Test
    fun `get returns null for unknown flag`() {
        assertNull(storage.get("unknown-flag", key1))
    }

    @Test
    fun `get returns null for unknown key`() {
        storage.upsert(change(key1, 1L, storedEval("flag-a", "on")))
        assertNull(storage.get("flag-a", key2))
    }

    @Test
    fun `upsert stores and get retrieves`() {
        val eval = storedEval("flag-a", "on")
        storage.upsert(change(key1, 1L, eval))

        assertEquals(eval, storage.get("flag-a", key1))
    }

    @Test
    fun `upsert updates existing entry`() {
        storage.upsert(change(key1, 1L, storedEval("flag-a", "on")))
        val updated = storedEval("flag-a", "off")
        storage.upsert(change(key1, 2L, updated))

        assertEquals(updated, storage.get("flag-a", key1))
    }

    @Test
    fun `get by flags returns only matching`() {
        storage.upsert(
            change(
                key1, 1L,
                storedEval("flag-a", "on"),
                storedEval("flag-b", "off"),
                storedEval("flag-c", "on"),
            )
        )

        val result = storage.get(setOf("flag-a", "flag-c"), key1)
        assertEquals(2, result.size)
        assertEquals("on", result["flag-a"]?.result?.treatment)
        assertEquals("on", result["flag-c"]?.result?.treatment)
        assertNull(result["flag-b"])
    }

    @Test
    fun `getByFlagSets filters by set intersection`() {
        storage.upsert(
            change(
                key1, 1L,
                storedEval("flag-a", "on", setOf("set1", "set2")),
                storedEval("flag-b", "off", setOf("set2", "set3")),
                storedEval("flag-c", "on", setOf("set3")),
            )
        )

        val result = storage.getByFlagSets(setOf("set1"), key1)
        assertEquals(1, result.size)
        assertEquals("on", result["flag-a"]?.result?.treatment)
    }

    @Test
    fun `getByFlagSets returns multiple flags matching any set`() {
        storage.upsert(
            change(
                key1, 1L,
                storedEval("flag-a", "on", setOf("set1")),
                storedEval("flag-b", "off", setOf("set2")),
                storedEval("flag-c", "on", setOf("set3")),
            )
        )

        val result = storage.getByFlagSets(setOf("set1", "set2"), key1)
        assertEquals(2, result.size)
        assertEquals("on", result["flag-a"]?.result?.treatment)
        assertEquals("off", result["flag-b"]?.result?.treatment)
    }

    @Test
    fun `getFlagNames returns stored flag names`() {
        storage.upsert(
            change(
                key1, 1L,
                storedEval("flag-a", "on"),
                storedEval("flag-b", "off"),
            )
        )

        val names = storage.getFlagNames(key1)
        assertEquals(setOf("flag-a", "flag-b"), names)
    }

    @Test
    fun `getFlagNames returns empty set for unknown key`() {
        assertEquals(emptySet<String>(), storage.getFlagNames(key1))
    }

    @Test
    fun `lastChangeNumber returns -1 for unknown key`() {
        assertEquals(-1L, storage.lastChangeNumber(key1))
    }

    @Test
    fun `lastChangeNumber returns stored value after upsert`() {
        storage.upsert(change(key1, 42L, storedEval("flag-a", "on")))
        assertEquals(42L, storage.lastChangeNumber(key1))
    }

    @Test
    fun `clear removes all data for key`() {
        storage.upsert(change(key1, 10L, storedEval("flag-a", "on")))
        storage.clear(key1)

        assertNull(storage.get("flag-a", key1))
        assertEquals(-1L, storage.lastChangeNumber(key1))
        assertEquals(emptySet<String>(), storage.getFlagNames(key1))
    }

    @Test
    fun `upsert skips update when changeNumber is same and flags are the same`() {
        storage.upsert(change(key1, 5L, storedEval("flag-a", "on")))
        storage.upsert(change(key1, 5L, storedEval("flag-a", "off")))

        assertEquals("on", storage.get("flag-a", key1)?.result?.treatment)
    }

    @Test
    fun `upsert skips update when changeNumber is older`() {
        storage.upsert(change(key1, 5L, storedEval("flag-a", "on")))
        storage.upsert(change(key1, 3L, storedEval("flag-a", "off")))

        assertEquals("on", storage.get("flag-a", key1)?.result?.treatment)
    }

    @Test
    fun `upsert applies update when flag is added with same changeNumber`() {
        storage.upsert(change(key1, 5L, storedEval("flag-a", "on")))
        storage.upsert(change(key1, 5L, storedEval("flag-a", "on"), storedEval("flag-b", "off")))

        assertEquals("off", storage.get("flag-b", key1)?.result?.treatment)
    }

    @Test
    fun `upsert applies update when flag is removed with same changeNumber`() {
        storage.upsert(change(key1, 5L, storedEval("flag-a", "on"), storedEval("flag-b", "off")))
        storage.upsert(change(key1, 5L, storedEval("flag-a", "on")))

        assertNull(storage.get("flag-b", key1))
    }

    @Test
    fun `different attributes mean different storage entries`() {
        storage.upsert(change(key1, 1L, storedEval("flag-a", "on")))
        storage.upsert(change(keyWithAttrs, 2L, storedEval("flag-a", "off")))

        assertEquals("on", storage.get("flag-a", key1)?.result?.treatment)
        assertEquals("off", storage.get("flag-a", keyWithAttrs)?.result?.treatment)
    }
}
