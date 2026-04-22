package io.split.client.thin.internal.persistence.domain.events

import org.junit.Test

class EventsPersistenceCallbacksTest {

    @Test
    fun `interface can be implemented`() {
        val callbacks = object : EventsPersistenceCallbacks {
            override fun onEventPushed() {}
            override fun onEventPopped(count: Int) {}
            override fun onPersistenceFailed(error: String) {}
        }
        callbacks.onEventPushed()
        callbacks.onEventPopped(5)
        callbacks.onPersistenceFailed("error")
    }
}
