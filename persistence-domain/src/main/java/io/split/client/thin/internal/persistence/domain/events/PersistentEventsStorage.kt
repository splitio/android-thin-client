package io.split.client.thin.internal.persistence.domain.events

import io.split.android.client.submitter.RecorderStorage
import io.split.android.client.submitter.StoragePusher
import io.split.android.client.tracker.TrackerEvent
import io.split.client.thin.internal.persistence.PersistentEventsStorage as RoomEventsPersistence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.IdentityHashMap

internal class PersistentEventsStorage(
    private val roomEventsPersistence: RoomEventsPersistence,
    private val serializer: TrackerEventSerializer,
    private val callbacks: EventsPersistenceCallbacks,
    private val scope: CoroutineScope
) : RecorderStorage<TrackerEvent>, StoragePusher<TrackerEvent> {

    private val poppedIds = IdentityHashMap<TrackerEvent, Long>()

    override fun push(element: TrackerEvent) {
        scope.launch {
            try {
                val json = serializer.serialize(element)
                roomEventsPersistence.push(json)
                callbacks.onEventPushed()
            } catch (e: Exception) {
                callbacks.onPersistenceFailed(e.message ?: "Unknown error")
            }
        }
    }

    override fun pop(count: Int): List<TrackerEvent> {
        return try {
            val stored = roomEventsPersistence.pop(count)
            val events = stored.map { serializer.deserialize(it.json) }
            events.forEachIndexed { i, event -> poppedIds[event] = stored[i].id }
            callbacks.onEventPopped(events.size)
            events
        } catch (e: Exception) {
            callbacks.onPersistenceFailed(e.message ?: "Unknown error")
            emptyList()
        }
    }

    override fun delete(items: List<TrackerEvent>) {
        try {
            val ids = items.mapNotNull { poppedIds.remove(it) }
            if (ids.isNotEmpty()) roomEventsPersistence.delete(ids)
        } catch (e: Exception) {
            callbacks.onPersistenceFailed(e.message ?: "Unknown error")
        }
    }

    override fun setActive(items: List<TrackerEvent>) {
        // no-op: events are still in storage (pop is non-destructive)
    }
}
