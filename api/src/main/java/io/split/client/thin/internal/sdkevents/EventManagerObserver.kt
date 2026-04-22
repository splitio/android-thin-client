package io.split.client.thin.internal.sdkevents

import io.harness.events.EventsManager
import io.split.client.thin.SplitEvent
import io.split.client.thin.internal.observer.ObservableEvent
import io.split.client.thin.internal.observer.ObservableEventType
import io.split.client.thin.internal.observer.Observer

internal class EventManagerObserver(
    private val eventsManager: EventsManager<SplitEvent, SdkInternalEvent, Any?>,
    @Volatile var matchingKey: String? = null,
) : Observer {

    override fun notifyEvent(event: ObservableEvent) {
        val internalEvent = EVENT_TYPE_MAP[event.type] ?: return
        if (matchingKey != null && event.type in KEY_SCOPED_EVENTS) {
            val eventKey = event.properties["matchingKey"] ?: return
            if (eventKey != matchingKey) return
        }
        eventsManager.notifyInternalEvent(internalEvent, event.payload)
    }

    companion object {
        private val KEY_SCOPED_EVENTS = setOf(
            ObservableEventType.EVAL_STORAGE_UPDATED,
            ObservableEventType.EVALUATIONS_UPDATED,
            ObservableEventType.EVAL_LOADED_FROM_STORAGE,
        )
        private val EVENT_TYPE_MAP = mapOf(
            ObservableEventType.EVAL_STORAGE_UPDATED to SdkInternalEvent.EVALUATIONS_SYNC_COMPLETE,
            ObservableEventType.EVAL_LOADED_FROM_STORAGE to SdkInternalEvent.EVALUATIONS_LOADED_FROM_STORAGE,
            ObservableEventType.SDK_READY_TIMEOUT_REACHED to SdkInternalEvent.SDK_READY_TIMEOUT_REACHED,
            ObservableEventType.EVALUATIONS_UPDATED to SdkInternalEvent.EVALUATIONS_UPDATED,
        )
    }
}
