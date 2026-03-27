package io.split.client.thin.internal.persistence

import io.split.android.client.tracker.TrackerEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RoomEventsPersistence(
    private val dao: EventDao
) : PersistentEventsStorage {

    private val json = Json { ignoreUnknownKeys = true }

    override fun push(event: TrackerEvent) {
        val dto = TrackerEventDto.fromTrackerEvent(event)
        val entity = EventEntity(
            0, // Auto-generated
            json.encodeToString(dto),
            event.timestamp
        )
        dao.insert(entity)
    }

    override fun pop(count: Int): List<TrackerEvent> {
        val entities = dao.getOldest(count)
        val events = entities.map { entity ->
            val dto = json.decodeFromString<TrackerEventDto>(entity.body)
            dto.toTrackerEvent()
        }

        if (entities.isNotEmpty()) {
            dao.delete(entities)
        }

        return events
    }

    override fun clear() {
        dao.deleteAll()
    }

    override fun count(): Int {
        return dao.count()
    }
}
