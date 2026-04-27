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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

private object AnyMapValueSerializer : KSerializer<Map<String, Any>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AnyMap")

    override fun serialize(encoder: Encoder, value: Map<String, Any>) {
        val filtered = value.filterValues { it != null }
        encoder.encodeSerializableValue(
            JsonElement.serializer(),
            buildJsonObject {
                filtered.forEach { (k, v) -> put(k, AnyValueSerializer.toJsonElement(v)) }
            }
        )
    }

    override fun deserialize(decoder: Decoder): Map<String, Any> {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        if (element !is JsonObject) return emptyMap()
        return element.entries
            .filter { (_, v) -> v !is JsonNull }
            .associate { (k, v) -> k to AnyValueSerializer.fromJsonElement(v) }
    }
}

private object AnyValueSerializer : KSerializer<Any> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Any")

    override fun serialize(encoder: Encoder, value: Any) {
        encoder.encodeSerializableValue(JsonElement.serializer(), toJsonElement(value))
    }

    override fun deserialize(decoder: Decoder): Any {
        return fromJsonElement((decoder as JsonDecoder).decodeJsonElement())
    }

    fun toJsonElement(value: Any): JsonElement = when (value) {
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is List<*> -> buildJsonArray {
            value.forEach { item -> if (item != null) add(toJsonElement(item)) }
        }
        is Map<*, *> -> buildJsonObject {
            value.forEach { (k, v) -> if (k is String && v != null) put(k, toJsonElement(v)) }
        }
        else -> JsonPrimitive(value.toString())
    }

    fun fromJsonElement(element: JsonElement): Any = when (element) {
        is JsonNull -> error("unexpected JsonNull after filter")
        is JsonArray -> element.map { fromJsonElement(it) }
        is JsonObject -> element.entries.associate { (k, v) -> k to fromJsonElement(v) }
        else -> {
            val prim = element.jsonPrimitive
            when {
                prim.booleanOrNull != null &&
                        (prim.content == "true" || prim.content == "false") -> prim.boolean
                prim.longOrNull != null -> prim.longOrNull!!
                prim.doubleOrNull != null -> prim.doubleOrNull!!
                else -> prim.content
            }
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
    @Serializable(with = AnyMapValueSerializer::class)
    val properties: Map<String, Any>? = null
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
