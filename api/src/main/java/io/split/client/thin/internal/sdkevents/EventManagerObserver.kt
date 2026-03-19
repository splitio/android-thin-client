package io.split.client.thin.internal.sdkevents

import io.harness.events.EventsManager
import io.split.client.thin.SplitEvent
import io.split.client.thin.internal.observer.ObservableEvent
import io.split.client.thin.internal.observer.Observer

internal class EventManagerObserver(
    private val eventsManager: EventsManager<SplitEvent, SdkInternalEvent, Any?>,
) : Observer {

    override fun notifyEvent(event: ObservableEvent) {
        val internalEvent = EVENT_TYPE_MAP[event.type] ?: return
        eventsManager.notifyInternalEvent(internalEvent, null)
    }

    companion object {
        private val EVENT_TYPE_MAP = mapOf(
            "eval_storage_updated" to SdkInternalEvent.EVALUATIONS_SYNC_COMPLETE,
            "eval_loaded_from_storage" to SdkInternalEvent.EVALUATIONS_LOADED_FROM_STORAGE,
            "sdk_ready_timeout_reached" to SdkInternalEvent.SDK_READY_TIMEOUT_REACHED,
            "evaluations_updated" to SdkInternalEvent.EVALUATIONS_UPDATED,
        )
    }
}
