package io.split.client.thin.internal.sdkevents

import io.harness.events.EventsManager
import io.split.client.thin.SdkReadyMetadata
import io.split.client.thin.SplitEvent
import io.split.client.thin.internal.observer.ObservableEvent
import org.junit.Assert.assertEquals
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

    // -------------------------------------------------------------------------
    // matchingKey scoping — observer ignores events for other targets
    // -------------------------------------------------------------------------

    @Test
    fun `eval_storage_updated for own matchingKey fires event`() {
        val scopedObserver = EventManagerObserver(eventsManager, "user_a")

        scopedObserver.notifyEvent(
            ObservableEvent("eval_storage_updated", mapOf("matchingKey" to "user_a"))
        )

        verify(eventsManager).notifyInternalEvent(SdkInternalEvent.EVALUATIONS_SYNC_COMPLETE, null)
    }

    @Test
    fun `eval_storage_updated for different matchingKey is ignored`() {
        val scopedObserver = EventManagerObserver(eventsManager, "user_a")

        scopedObserver.notifyEvent(
            ObservableEvent("eval_storage_updated", mapOf("matchingKey" to "user_b"))
        )

        verifyNoInteractions(eventsManager)
    }

    @Test
    fun `evaluations_updated for different matchingKey is ignored`() {
        val scopedObserver = EventManagerObserver(eventsManager, "user_a")

        scopedObserver.notifyEvent(
            ObservableEvent("evaluations_updated", mapOf("matchingKey" to "user_b"))
        )

        verifyNoInteractions(eventsManager)
    }

    @Test
    fun `sdk_ready_timeout_reached fires regardless of matchingKey`() {
        val scopedObserver = EventManagerObserver(eventsManager, "user_a")

        scopedObserver.notifyEvent(ObservableEvent("sdk_ready_timeout_reached"))

        verify(eventsManager).notifyInternalEvent(SdkInternalEvent.SDK_READY_TIMEOUT_REACHED, null)
    }

    @Test
    fun `observer reacts to new matchingKey after update`() {
        val scopedObserver = EventManagerObserver(eventsManager, "user_a")
        scopedObserver.matchingKey = "user_b"

        scopedObserver.notifyEvent(
            ObservableEvent("evaluations_updated", mapOf("matchingKey" to "user_b"))
        )

        verify(eventsManager).notifyInternalEvent(SdkInternalEvent.EVALUATIONS_UPDATED, null)
    }

    @Test
    fun `observer ignores old matchingKey after update`() {
        val scopedObserver = EventManagerObserver(eventsManager, "user_a")
        scopedObserver.matchingKey = "user_b"

        scopedObserver.notifyEvent(
            ObservableEvent("evaluations_updated", mapOf("matchingKey" to "user_a"))
        )

        verifyNoInteractions(eventsManager)
    }

    // -------------------------------------------------------------------------
    // payload forwarding
    // -------------------------------------------------------------------------

    @Test
    fun `event payload is forwarded to notifyInternalEvent`() {
        val metadata = SdkReadyMetadata(isInitialCacheLoad = true, lastUpdateTimestamp = 123L)
        observer.notifyEvent(ObservableEvent("eval_loaded_from_storage", payload = metadata))

        verify(eventsManager).notifyInternalEvent(SdkInternalEvent.EVALUATIONS_LOADED_FROM_STORAGE, metadata)
    }

    @Test
    fun `null payload is forwarded as null`() {
        observer.notifyEvent(ObservableEvent("eval_storage_updated", payload = null))

        verify(eventsManager).notifyInternalEvent(SdkInternalEvent.EVALUATIONS_SYNC_COMPLETE, null)
    }

    // -------------------------------------------------------------------------
    // EVAL_LOADED_FROM_STORAGE is KEY_SCOPED
    // -------------------------------------------------------------------------

    @Test
    fun `eval_loaded_from_storage for different matchingKey is ignored`() {
        val scopedObserver = EventManagerObserver(eventsManager, "user_a")

        scopedObserver.notifyEvent(
            ObservableEvent("eval_loaded_from_storage", mapOf("matchingKey" to "user_b"))
        )

        verifyNoInteractions(eventsManager)
    }

    @Test
    fun `eval_loaded_from_storage for own matchingKey fires event with payload`() {
        val metadata = SdkReadyMetadata(isInitialCacheLoad = false, lastUpdateTimestamp = 999L)
        val scopedObserver = EventManagerObserver(eventsManager, "user_a")

        scopedObserver.notifyEvent(
            ObservableEvent("eval_loaded_from_storage", mapOf("matchingKey" to "user_a"), payload = metadata)
        )

        verify(eventsManager).notifyInternalEvent(SdkInternalEvent.EVALUATIONS_LOADED_FROM_STORAGE, metadata)
    }
}
