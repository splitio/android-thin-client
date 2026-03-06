package io.split.client.thin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EvaluationResultTest {

    @Test
    fun `evaluation result constructor`() {
        val evaluationResult = EvaluationResult(
            flag = "test-flag",
            treatment = "test-treatment"
        )
        assertEquals("test-flag", evaluationResult.flag)
        assertEquals("test-treatment", evaluationResult.treatment)
        assertNull(evaluationResult.config)
        assertNull(evaluationResult.label)
        assertNull(evaluationResult.changeNumber)
    }

    @Test
    fun `evaluation result constructor with change number`() {
        val evaluationResult = EvaluationResult(
            flag = "test-flag",
            treatment = "test-treatment",
            changeNumber = 1234567890L
        )
        assertEquals("test-flag", evaluationResult.flag)
        assertEquals("test-treatment", evaluationResult.treatment)
        assertNull(evaluationResult.config)
        assertNull(evaluationResult.label)
        assertEquals(1234567890L, evaluationResult.changeNumber)
    }

    @Test
    fun `evaluation result constructor with config and label`() {
        val evaluationResult = EvaluationResult(
            flag = "test-flag",
            treatment = "test-treatment",
            config = "test-config",
            label = "test-label",
            changeNumber = 1234567890L,
        )
        assertEquals("test-flag", evaluationResult.flag)
        assertEquals("test-treatment", evaluationResult.treatment)
        assertEquals("test-config", evaluationResult.config)
        assertEquals("test-label", evaluationResult.label)
        assertEquals(1234567890L, evaluationResult.changeNumber)
    }

    @Test
    fun `evaluation result constructor with all fields nullables omitted except label`() {
        val evaluationResult = EvaluationResult(
            flag = "test-flag",
            treatment = "test-treatment",
            label = "test-label"
        )
        assertEquals("test-flag", evaluationResult.flag)
        assertEquals("test-treatment", evaluationResult.treatment)
        assertNull(evaluationResult.config)
        assertEquals("test-label", evaluationResult.label)
        assertNull(evaluationResult.changeNumber)
    }
}
