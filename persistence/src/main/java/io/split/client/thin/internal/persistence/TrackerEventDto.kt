package io.split.client.thin.internal.persistence

import io.split.android.client.tracker.TrackerEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class TrackerEventDto(
    val trafficType: String,
    val eventType: String,
    val key: String,
    val value: Double,
    val timestamp: Long,
    val properties: Map<String, JsonElement>,
    val sizeInBytes: Int
) {
    fun toTrackerEvent(): TrackerEvent {
        return TrackerEvent().apply {
            this.trafficType = this@TrackerEventDto.trafficType
            this.eventType = this@TrackerEventDto.eventType
            this.key = this@TrackerEventDto.key
            this.value = this@TrackerEventDto.value
            this.timestamp = this@TrackerEventDto.timestamp
            this.properties = this@TrackerEventDto.properties.mapValues { (_, v) ->
                when (v) {
                    is JsonPrimitive -> {
                        when {
                            v.isString -> v.content
                            else -> v.content.toDoubleOrNull() ?: v.content
                        }
                    }
                    else -> v.toString()
                }
            }
            this.sizeInBytes = this@TrackerEventDto.sizeInBytes
        }
    }

    companion object {
        fun fromTrackerEvent(event: TrackerEvent): TrackerEventDto {
            return TrackerEventDto(
                trafficType = event.trafficType,
                eventType = event.eventType,
                key = event.key,
                value = event.value,
                timestamp = event.timestamp,
                properties = event.properties.mapValues { (_, v) ->
                    when (v) {
                        is String -> JsonPrimitive(v)
                        is Number -> JsonPrimitive(v)
                        is Boolean -> JsonPrimitive(v)
                        else -> JsonPrimitive(v.toString())
                    }
                },
                sizeInBytes = event.sizeInBytes
            )
        }
    }
}
