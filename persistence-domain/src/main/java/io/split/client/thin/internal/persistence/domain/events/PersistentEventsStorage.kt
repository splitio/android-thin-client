package io.split.client.thin.internal.persistence.domain.events

import io.split.android.client.submitter.RecorderStorage
import io.split.android.client.submitter.StoragePusher
import io.split.android.client.tracker.TrackerEvent
import io.split.client.thin.internal.persistence.PersistentEventsStorage as RoomEventsPersistence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

internal class PersistentEventsStorage(
    private val roomEventsPersistence: RoomEventsPersistence,
    private val serializer: TrackerEventSerializer,
    private val callbacks: EventsPersistenceCallbacks,
    private val scope: CoroutineScope
) : RecorderStorage<TrackerEvent>, StoragePusher<TrackerEvent> {

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
            val jsons = roomEventsPersistence.pop(count)
            val events = jsons.map { serializer.deserialize(it) }
            callbacks.onEventPopped(events.size)
            events
        } catch (e: Exception) {
            callbacks.onPersistenceFailed(e.message ?: "Unknown error")
            emptyList()
        }
    }

    override fun delete(items: List<TrackerEvent>) {
        // no-op: items are already removed by pop() in Room
    }

    override fun setActive(items: List<TrackerEvent>) {
        items.forEach { push(it) }
    }
}
