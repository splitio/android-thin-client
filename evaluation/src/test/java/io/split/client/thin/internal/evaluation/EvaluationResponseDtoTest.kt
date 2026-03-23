package io.split.client.thin.internal.evaluation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationResponseDtoTest {

    // ─── EvaluationDto ───────────────────────────────────────────────────────

    @Test
    fun `EvaluationDto sets defaults for optional fields`() {
        val dto = EvaluationDto(featureName = "flag", treatment = "on")
        assertTrue(dto.sets.isEmpty())
        assertNull(dto.config)
    }

    @Test
    fun `EvaluationDto holds all provided values`() {
        val dto = EvaluationDto(
            featureName = "flag",
            treatment = "off",
            sets = listOf("a", "b"),
            config = """{"k":"v"}""",
        )
        assertEquals("flag", dto.featureName)
        assertEquals("off", dto.treatment)
        assertEquals(listOf("a", "b"), dto.sets)
        assertEquals("""{"k":"v"}""", dto.config)
    }

    @Test
    fun `EvaluationDto equals and hashCode are consistent`() {
        val a = EvaluationDto(featureName = "f", treatment = "on", sets = listOf("s"), config = null)
        val b = EvaluationDto(featureName = "f", treatment = "on", sets = listOf("s"), config = null)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `EvaluationDto not equal when fields differ`() {
        val base = EvaluationDto(featureName = "f", treatment = "on")
        assertNotEquals(base, base.copy(featureName = "other"))
        assertNotEquals(base, base.copy(treatment = "off"))
        assertNotEquals(base, base.copy(sets = listOf("s")))
        assertNotEquals(base, base.copy(config = "cfg"))
    }

    @Test
    fun `EvaluationDto copy overrides individual fields`() {
        val original = EvaluationDto(featureName = "flag", treatment = "on")
        val copied = original.copy(treatment = "off", config = "c")
        assertEquals("flag", copied.featureName)
        assertEquals("off", copied.treatment)
        assertEquals("c", copied.config)
    }

    @Test
    fun `EvaluationDto toString contains field values`() {
        val dto = EvaluationDto(featureName = "flag", treatment = "on")
        val str = dto.toString()
        assertTrue(str.contains("flag"))
        assertTrue(str.contains("on"))
    }

    // ─── EvaluationsResponseDto ───────────────────────────────────────────────

    @Test
    fun `EvaluationsResponseDto defaults to empty with negative timestamps`() {
        val dto = EvaluationsResponseDto()
        assertEquals(-1L, dto.till)
        assertEquals(-1L, dto.since)
        assertTrue(dto.evaluations.isEmpty())
    }

    @Test
    fun `EvaluationsResponseDto holds till and since independently`() {
        val dto = EvaluationsResponseDto(till = 1000L, since = 500L)
        assertEquals(1000L, dto.till)
        assertEquals(500L, dto.since)
    }

    @Test
    fun `EvaluationsResponseDto holds evaluations list`() {
        val eval = EvaluationDto(featureName = "f", treatment = "on")
        val dto = EvaluationsResponseDto(till = 1L, since = 0L, evaluations = listOf(eval))
        assertEquals(1, dto.evaluations.size)
        assertEquals(eval, dto.evaluations[0])
    }

    @Test
    fun `EvaluationsResponseDto equals and hashCode are consistent`() {
        val a = EvaluationsResponseDto(till = 1L, since = 0L, evaluations = emptyList())
        val b = EvaluationsResponseDto(till = 1L, since = 0L, evaluations = emptyList())
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `EvaluationsResponseDto not equal when fields differ`() {
        val base = EvaluationsResponseDto(till = 1L, since = 0L)
        assertNotEquals(base, base.copy(till = 2L))
        assertNotEquals(base, base.copy(since = 1L))
        val eval = EvaluationDto(featureName = "f", treatment = "on")
        assertNotEquals(base, base.copy(evaluations = listOf(eval)))
    }

    @Test
    fun `EvaluationsResponseDto copy overrides individual fields`() {
        val original = EvaluationsResponseDto(till = 1L, since = 0L)
        val copied = original.copy(till = 99L)
        assertEquals(99L, copied.till)
        assertEquals(0L, copied.since)
    }

    @Test
    fun `EvaluationsResponseDto toString contains field values`() {
        val dto = EvaluationsResponseDto(till = 42L, since = 7L)
        val str = dto.toString()
        assertTrue(str.contains("42"))
        assertTrue(str.contains("7"))
    }

    // ─── JSON deserialization — default value branches ─────────────────────────

    @Test
    fun `EvaluationDto deserializes with absent optional sets field`() {
        val deserializer = JsonEvaluationResponseDeserializer()
        val json = """{"till": 1, "since": -1, "evaluations": [{"featureName": "f", "treatment": "on"}]}"""
        val result = deserializer.deserialize(json, EvaluationKey(io.split.client.thin.Key("u")))
        assertTrue(result.evaluations[0].flagSets.isEmpty())
    }

    @Test
    fun `EvaluationDto deserializes with absent config field`() {
        val deserializer = JsonEvaluationResponseDeserializer()
        val json = """{"till": 1, "since": -1, "evaluations": [{"featureName": "f", "treatment": "on"}]}"""
        val result = deserializer.deserialize(json, EvaluationKey(io.split.client.thin.Key("u")))
        assertNull(result.evaluations[0].result.config)
    }

    @Test
    fun `deserializer maps multiple evaluations`() {
        val deserializer = JsonEvaluationResponseDeserializer()
        val json = """
            {
                "till": 5, "since": 3,
                "evaluations": [
                    {"featureName": "f1", "treatment": "on"},
                    {"featureName": "f2", "treatment": "off"}
                ]
            }
        """.trimIndent()
        val result = deserializer.deserialize(json, EvaluationKey(io.split.client.thin.Key("u")))
        assertEquals(2, result.evaluations.size)
        assertEquals("f1", result.evaluations[0].result.flag)
        assertEquals("f2", result.evaluations[1].result.flag)
    }

    @Test
    fun `deserializer preserves evaluationKey in returned EvaluationChange`() {
        val deserializer = JsonEvaluationResponseDeserializer()
        val key = EvaluationKey(io.split.client.thin.Key("matching", "bucketing"))
        val json = """{"till": 1, "since": -1, "evaluations": []}"""
        val result = deserializer.deserialize(json, key)
        assertEquals(key, result.evaluationKey)
    }

    @Test
    fun `EvaluationsResponseDto with since field parsed from JSON`() {
        val deserializer = JsonEvaluationResponseDeserializer()
        // Verify the `since` field doesn't cause parsing errors and the response is valid
        val json = """{"till": 100, "since": 50, "evaluations": []}"""
        val result = deserializer.deserialize(json, EvaluationKey(io.split.client.thin.Key("u")))
        assertEquals(100L, result.changeNumber)
        assertFalse(result.evaluations.isNotEmpty())
    }
}
