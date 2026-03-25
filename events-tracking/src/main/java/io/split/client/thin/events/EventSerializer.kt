package io.split.client.thin.events

import io.split.android.client.tracker.TrackerEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object EventSerializer {

    private val json = Json { encodeDefaults = true }

    fun serialize(events: List<TrackerEvent>): String {
        val dtos = events.map { it.toDto() }
        return json.encodeToString(dtos)
    }

    private fun TrackerEvent.toDto(): EventDto {
        return EventDto(
            key = this.key,
            trafficTypeName = this.trafficType,
            eventTypeId = this.eventType,
            value = this.value,
            timestamp = this.timestamp,
            properties = this.properties
        )
    }

    @Serializable
    private data class EventDto(
        val key: String,
        val trafficTypeName: String,
        val eventTypeId: String,
        val value: Double,
        val timestamp: Long,
        val properties: Map<String, @Serializable(with = AnySerializer::class) Any>?
    )
}
