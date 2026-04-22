package io.split.client.thin.internal.persistence.domain

import io.split.client.thin.events.EventsStorage
import kotlinx.coroutines.test.TestScope
import org.junit.Test
import org.mockito.Mockito.mock
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistenceConfigTest {

    @Test
    fun `default config has enabled true and null prefix`() {
        val config = PersistenceConfig()
        assertTrue(config.enabled)
        assertNull(config.prefix)
    }

    @Test
    fun `config can be disabled`() {
        val config = PersistenceConfig(enabled = false)
        assertTrue(!config.enabled)
    }

    @Test
    fun `config can have prefix`() {
        val config = PersistenceConfig(prefix = "myApp")
        assertNotNull(config.prefix)
    }
}

class CreatePersistenceDomainComponentsTest {

    @Test
    fun `when disabled returns null evaluationPersistenceManager and EventsStorage`() {
        val config = PersistenceConfig(enabled = false)
        val evaluationCallbacks = mock(
            io.split.client.thin.internal.persistence.domain.evaluation.EvaluationPersistenceCallbacks::class.java
        )
        val eventsCallbacks = mock(
            io.split.client.thin.internal.persistence.domain.events.EventsPersistenceCallbacks::class.java
        )
        val scope = TestScope()

        val components = createPersistenceDomainComponents(
            context = mock(android.content.Context::class.java),
            config = config,
            evaluationCallbacks = evaluationCallbacks,
            eventsCallbacks = eventsCallbacks,
            scope = scope
        )

        assertNull(components.evaluationPersistenceManager)
        assertTrue(components.eventsStorage is EventsStorage)
    }
}
