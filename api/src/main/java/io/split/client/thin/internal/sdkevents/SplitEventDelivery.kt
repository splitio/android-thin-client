package io.split.client.thin.internal.sdkevents

import io.harness.events.EventDelivery
import io.harness.events.EventHandler
import io.split.client.thin.SplitEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class SplitEventDelivery(
    private val scope: CoroutineScope,
) : EventDelivery<SplitEvent, Any?> {

    override fun deliver(eventHandler: EventHandler<SplitEvent, Any?>, event: SplitEvent, metadata: Any?) {
        scope.launch {
            try {
                eventHandler.handle(event, metadata)
            } catch (_: Exception) {
                // fault isolation: exceptions in handlers must not affect other delivery
            }
        }
    }
}
