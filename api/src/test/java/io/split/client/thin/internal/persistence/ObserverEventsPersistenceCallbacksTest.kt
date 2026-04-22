package io.split.client.thin.internal.persistence

import io.split.client.thin.internal.observer.CompositeObserver
import io.split.client.thin.internal.observer.ObservableEvent
import io.split.client.thin.internal.observer.ObservableEventType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class ObserverEventsPersistenceCallbacksTest {

    private lateinit var compositeObserver: CompositeObserver
    private lateinit var callbacks: ObserverEventsPersistenceCallbacks

    @Before
    fun setUp() {
        compositeObserver = mock(CompositeObserver::class.java)
        callbacks = ObserverEventsPersistenceCallbacks(compositeObserver)
    }

    private fun captureEvent(): ObservableEvent {
        val captor = ArgumentCaptor.forClass(ObservableEvent::class.java)
        verify(compositeObserver).notifyEvent(captor.capture() ?: ObservableEvent(""))
        return captor.value
    }

    @Test
    fun `onEventPushed dispatches EVENT_PUSHED event`() {
        callbacks.onEventPushed()

        assertEquals(ObservableEventType.EVENT_PUSHED, captureEvent().type)
    }

    @Test
    fun `onEventPopped dispatches EVENT_POPPED event`() {
        callbacks.onEventPopped(3)

        assertEquals(ObservableEventType.EVENT_POPPED, captureEvent().type)
    }

    @Test
    fun `onPersistenceFailed dispatches PERSISTENCE_FAILED event`() {
        callbacks.onPersistenceFailed("storage unavailable")

        assertEquals(ObservableEventType.PERSISTENCE_FAILED, captureEvent().type)
    }
}
