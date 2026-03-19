package io.split.client.thin.internal.sdkevents

import io.harness.events.EventsManager
import io.split.client.thin.SplitEvent
import io.split.client.thin.internal.observer.ObservableEvent
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

@Suppress("UNCHECKED_CAST")
class EventManagerObserverTest {

    private lateinit var eventsManager: EventsManager<SplitEvent, SdkInternalEvent, Any?>
    private lateinit var observer: EventManagerObserver

    @Before
    fun setUp() {
        eventsManager = mock(EventsManager::class.java) as EventsManager<SplitEvent, SdkInternalEvent, Any?>
        observer = EventManagerObserver(eventsManager)
    }

    @Test
    fun `eval_storage_updated maps to EVALUATIONS_SYNC_COMPLETE`() {
        observer.notifyEvent(ObservableEvent("eval_storage_updated"))

        verify(eventsManager).notifyInternalEvent(SdkInternalEvent.EVALUATIONS_SYNC_COMPLETE, null)
    }

    @Test
    fun `eval_loaded_from_storage maps to EVALUATIONS_LOADED_FROM_STORAGE`() {
        observer.notifyEvent(ObservableEvent("eval_loaded_from_storage"))

        verify(eventsManager).notifyInternalEvent(SdkInternalEvent.EVALUATIONS_LOADED_FROM_STORAGE, null)
    }

    @Test
    fun `sdk_ready_timeout_reached maps to SDK_READY_TIMEOUT_REACHED`() {
        observer.notifyEvent(ObservableEvent("sdk_ready_timeout_reached"))

        verify(eventsManager).notifyInternalEvent(SdkInternalEvent.SDK_READY_TIMEOUT_REACHED, null)
    }

    @Test
    fun `evaluations_updated maps to EVALUATIONS_UPDATED`() {
        observer.notifyEvent(ObservableEvent("evaluations_updated"))

        verify(eventsManager).notifyInternalEvent(SdkInternalEvent.EVALUATIONS_UPDATED, null)
    }

    @Test
    fun `unknown event type is silently ignored`() {
        observer.notifyEvent(ObservableEvent("unknown_event"))

        verifyNoInteractions(eventsManager)
    }
}
