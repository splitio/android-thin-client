package io.split.client.thin.internal.persistence.domain.evaluation

import io.split.client.thin.EvaluationResult
import io.split.client.thin.internal.evaluation.StoredEvaluation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoredEvaluationSerializerTest {

    private val serializer = StoredEvaluationSerializer()

    @Test
    fun `serialize and deserialize basic evaluation`() {
        val evaluation = StoredEvaluation(
            result = EvaluationResult(flag = "my_flag", treatment = "on")
        )
        val json = serializer.serialize(evaluation)
        val result = serializer.deserialize(json)
        assertEquals("my_flag", result.result.flag)
        assertEquals("on", result.result.treatment)
        assertNull(result.result.config)
        assertNull(result.result.changeNumber)
        assertEquals(emptySet<String>(), result.flagSets)
    }

    @Test
    fun `serialize and deserialize full evaluation`() {
        val evaluation = StoredEvaluation(
            result = EvaluationResult(
                flag = "feature",
                treatment = "variant_a",
                config = "{\"key\":\"value\"}",
                changeNumber = 1234567890L
            ),
            flagSets = setOf("set1", "set2")
        )
        val json = serializer.serialize(evaluation)
        val result = serializer.deserialize(json)
        assertEquals("feature", result.result.flag)
        assertEquals("variant_a", result.result.treatment)
        assertEquals("{\"key\":\"value\"}", result.result.config)
        assertEquals(1234567890L, result.result.changeNumber)
        assertEquals(setOf("set1", "set2"), result.flagSets)
    }

    @Test
    fun `deserialize preserves flag sets`() {
        val evaluation = StoredEvaluation(
            result = EvaluationResult(flag = "f", treatment = "off"),
            flagSets = setOf("a", "b", "c")
        )
        val result = serializer.deserialize(serializer.serialize(evaluation))
        assertEquals(setOf("a", "b", "c"), result.flagSets)
    }
}
