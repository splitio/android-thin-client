package io.split.client.thin.internal

import io.split.client.thin.Key
import io.split.client.thin.Target
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultClientFactoryTest {

    @Test
    fun `invoke creates DefaultSplitClient`() {
        val factory = DefaultClientFactory(
            readStorage = FakeEvaluationReadStorage(),
            evaluationRepository = FakeEvaluationRepository(),
            filters = null,
            fallbackCalculator = null,
        )

        val client = factory(Target(Key("user-1")))

        assertTrue(client is DefaultSplitClient)
    }
}
