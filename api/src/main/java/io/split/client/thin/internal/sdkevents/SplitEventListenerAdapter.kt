package io.split.client.thin.internal.sdkevents

import io.harness.events.EventHandler
import io.harness.events.EventsManager
import io.split.client.thin.SdkReadyMetadata
import io.split.client.thin.SdkUpdateMetadata
import io.split.client.thin.SplitClient
import io.split.client.thin.SplitEvent
import io.split.client.thin.SplitEventListener

internal class SplitEventListenerAdapter(
    private val listener: SplitEventListener,
    private val client: SplitClient,
) {

    fun registerAll(eventsManager: EventsManager<SplitEvent, SdkInternalEvent, Any?>) {
        eventsManager.register(SplitEvent.SDK_READY, EventHandler { _, metadata ->
            listener.onReady(client, metadata as? SdkReadyMetadata)
            listener.onReadyView(client, metadata as? SdkReadyMetadata)
        })
        eventsManager.register(SplitEvent.SDK_READY_FROM_CACHE, EventHandler { _, metadata ->
            listener.onReadyFromCache(client, metadata as? SdkReadyMetadata)
            listener.onReadyFromCacheView(client, metadata as? SdkReadyMetadata)
        })
        eventsManager.register(SplitEvent.SDK_READY_TIMEOUT, EventHandler { _, _ ->
            listener.onTimeout(client)
            listener.onTimeoutView(client)
        })
        eventsManager.register(SplitEvent.SDK_UPDATE, EventHandler { _, metadata ->
            listener.onUpdate(client, metadata as? SdkUpdateMetadata)
            listener.onUpdateView(client, metadata as? SdkUpdateMetadata)
        })
    }
}
