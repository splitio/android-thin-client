package io.split.client.thin.internal.evaluation

import io.split.client.thin.Key
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonEvaluationResponseDeserializerTest {

    private val deserializer = JsonEvaluationResponseDeserializer()
    private val evalKey = EvaluationKey(Key("user-1"))

    @Test
    fun `deserializes real API format correctly`() {
        val json = """
            {
                "till": 1772129027764,
                "since": -1,
                "evaluations": [
                    {"flag": "flag_name", "treatment": "on", "sets": ["set1"], "config": null}
                ]
            }
        """.trimIndent()

        val result = deserializer.deserialize(json, evalKey)
        assertEquals(1, result.evaluations.size)
        assertEquals(1772129027764L, result.changeNumber)
    }

    @Test
    fun `maps featureName to flag`() {
        val json = """{"till": 1, "since": -1, "evaluations": [{"flag": "my-flag", "treatment": "on", "sets": []}]}"""
        val result = deserializer.deserialize(json, evalKey)
        assertEquals("my-flag", result.evaluations[0].result.flag)
    }

    @Test
    fun `maps treatment correctly`() {
        val json = """{"till": 1, "since": -1, "evaluations": [{"flag": "f", "treatment": "control", "sets": []}]}"""
        val result = deserializer.deserialize(json, evalKey)
        assertEquals("control", result.evaluations[0].result.treatment)
    }

    @Test
    fun `maps config correctly`() {
        val json = """{"till": 1, "since": -1, "evaluations": [{"flag": "f", "treatment": "on", "sets": [], "config": "{\"key\":\"val\"}"}]}"""
        val result = deserializer.deserialize(json, evalKey)
        assertEquals("{\"key\":\"val\"}", result.evaluations[0].result.config)
    }

    @Test
    fun `maps null config correctly`() {
        val json = """{"till": 1, "since": -1, "evaluations": [{"flag": "f", "treatment": "on", "sets": [], "config": null}]}"""
        val result = deserializer.deserialize(json, evalKey)
        assertNull(result.evaluations[0].result.config)
    }

    @Test
    fun `maps sets to flagSets`() {
        val json = """{"till": 1, "since": -1, "evaluations": [{"flag": "f", "treatment": "on", "sets": ["set1", "set2"]}]}"""
        val result = deserializer.deserialize(json, evalKey)
        assertEquals(setOf("set1", "set2"), result.evaluations[0].flagSets)
    }

    @Test
    fun `maps till to changeNumber`() {
        val json = """{"till": 999888777, "since": -1, "evaluations": []}"""
        val result = deserializer.deserialize(json, evalKey)
        assertEquals(999888777L, result.changeNumber)
    }

    @Test
    fun `empty evaluations list`() {
        val json = """{"till": -1, "since": -1, "evaluations": []}"""
        val result = deserializer.deserialize(json, evalKey)
        assertTrue(result.evaluations.isEmpty())
    }

    @Test
    fun `ignores unknown JSON fields`() {
        val json = """{"till": 1, "since": -1, "unknown": "value", "evaluations": []}"""
        val result = deserializer.deserialize(json, evalKey)
        assertEquals(1L, result.changeNumber)
    }

    @Test(expected = SerializationException::class)
    fun `throws on malformed JSON`() {
        deserializer.deserialize("not-json", evalKey)
    }
}
