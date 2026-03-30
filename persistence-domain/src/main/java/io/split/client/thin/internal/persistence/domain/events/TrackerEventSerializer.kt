package io.split.client.thin.internal.persistence.domain.events

import io.split.android.client.tracker.TrackerEvent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

private object AnyValueSerializer : KSerializer<Any> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Any")

    override fun serialize(encoder: Encoder, value: Any) {
        val element = when (value) {
            is String -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
        }
        encoder.encodeSerializableValue(JsonElement.serializer(), element)
    }

    override fun deserialize(decoder: Decoder): Any {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        return when {
            element is JsonNull -> throw IllegalStateException("Unexpected null in properties map")
            element.jsonPrimitive.booleanOrNull != null &&
                    (element.jsonPrimitive.content == "true" || element.jsonPrimitive.content == "false") ->
                element.jsonPrimitive.boolean
            element.jsonPrimitive.longOrNull != null -> element.jsonPrimitive.longOrNull!!
            element.jsonPrimitive.doubleOrNull != null -> element.jsonPrimitive.doubleOrNull!!
            else -> element.jsonPrimitive.content
        }
    }
}

@Serializable
private data class TrackerEventDto(
    val trafficType: String,
    val eventType: String,
    val key: String,
    val value: Double,
    val timestamp: Long,
    val properties: Map<String, @Serializable(with = AnyValueSerializer::class) Any>? = null
)

internal class TrackerEventSerializer(private val cipher: Any? = null) {

    private val json = Json { ignoreUnknownKeys = true }

    fun serialize(event: TrackerEvent): String {
        val dto = TrackerEventDto(
            trafficType = event.trafficType,
            eventType = event.eventType,
            key = event.key,
            value = event.value,
            timestamp = event.timestamp,
            properties = event.properties
            ?.filterValues { it != null }
            ?.mapValues { it.value as Any }
            ?.takeIf { it.isNotEmpty() }
        )
        return json.encodeToString(dto)
    }

    fun deserialize(serialized: String): TrackerEvent {
        val dto = json.decodeFromString<TrackerEventDto>(serialized)
        return TrackerEvent().apply {
            trafficType = dto.trafficType
            eventType = dto.eventType
            key = dto.key
            value = dto.value
            timestamp = dto.timestamp
            properties = dto.properties
        }
    }
}
