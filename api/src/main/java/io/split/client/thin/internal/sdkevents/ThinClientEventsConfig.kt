package io.split.client.thin.internal.sdkevents

import io.harness.events.EventsManagerConfig
import io.split.client.thin.SplitEvent

internal object ThinClientEventsConfig {

    fun create(): EventsManagerConfig<SplitEvent, SdkInternalEvent> {
        return EventsManagerConfig.builder<SplitEvent, SdkInternalEvent>()
            // SDK_READY requires EVALUATIONS_SYNC_COMPLETE (AND)
            .requireAll(SplitEvent.SDK_READY, SdkInternalEvent.EVALUATIONS_SYNC_COMPLETE)
            // SDK_READY_FROM_CACHE fires when EVALUATIONS_LOADED_FROM_STORAGE OR EVALUATIONS_SYNC_COMPLETE
            .requireAny(
                SplitEvent.SDK_READY_FROM_CACHE,
                SdkInternalEvent.EVALUATIONS_LOADED_FROM_STORAGE,
                SdkInternalEvent.EVALUATIONS_SYNC_COMPLETE,
            )
            // SDK_READY_TIMEOUT fires when SDK_READY_TIMEOUT_REACHED
            .requireAny(SplitEvent.SDK_READY_TIMEOUT, SdkInternalEvent.SDK_READY_TIMEOUT_REACHED)
            // SDK_UPDATE fires when EVALUATIONS_UPDATED
            .requireAny(SplitEvent.SDK_UPDATE, SdkInternalEvent.EVALUATIONS_UPDATED)
            // SDK_READY requires SDK_READY_FROM_CACHE as prerequisite
            .prerequisite(SplitEvent.SDK_READY, SplitEvent.SDK_READY_FROM_CACHE)
            // SDK_UPDATE requires SDK_READY as prerequisite
            .prerequisite(SplitEvent.SDK_UPDATE, SplitEvent.SDK_READY)
            // SDK_READY_TIMEOUT is suppressed by SDK_READY
            .suppressedBy(SplitEvent.SDK_READY_TIMEOUT, SplitEvent.SDK_READY)
            // Execution limits
            .executionLimit(SplitEvent.SDK_READY, 1)
            .executionLimit(SplitEvent.SDK_READY_FROM_CACHE, 1)
            .executionLimit(SplitEvent.SDK_READY_TIMEOUT, 1)
            .executionLimit(SplitEvent.SDK_UPDATE, -1)
            .build()
    }
}
