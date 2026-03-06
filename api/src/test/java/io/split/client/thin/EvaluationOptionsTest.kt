package io.split.client.thin

import org.junit.Assert.assertNotNull
import org.junit.Test

class EvaluationOptionsTest {

    @Test
    fun `evaluation options can be created`() {
        val options = EvaluationOptions()
        assertNotNull(options)
    }
}
