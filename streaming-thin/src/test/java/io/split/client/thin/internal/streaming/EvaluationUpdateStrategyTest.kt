package io.split.client.thin.internal.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EvaluationUpdateStrategyTest {

    @Test
    fun `from null returns UNBOUNDED_FETCH_REQUEST`() {
        assertEquals(
            EvaluationUpdateStrategy.UNBOUNDED_FETCH_REQUEST,
            EvaluationUpdateStrategy.from(null)
        )
    }

    @Test
    fun `from 0 returns UNBOUNDED_FETCH_REQUEST`() {
        assertEquals(
            EvaluationUpdateStrategy.UNBOUNDED_FETCH_REQUEST,
            EvaluationUpdateStrategy.from(0)
        )
    }

    @Test
    fun `from 1 returns BOUNDED_FETCH_REQUEST`() {
        assertEquals(
            EvaluationUpdateStrategy.BOUNDED_FETCH_REQUEST,
            EvaluationUpdateStrategy.from(1)
        )
    }

    @Test
    fun `from 2 returns null (unimplemented strategy)`() {
        assertNull(EvaluationUpdateStrategy.from(2))
    }

    @Test
    fun `from 3 returns null (unimplemented strategy)`() {
        assertNull(EvaluationUpdateStrategy.from(3))
    }

    @Test
    fun `from unknown positive value returns null`() {
        assertNull(EvaluationUpdateStrategy.from(99))
    }
}
