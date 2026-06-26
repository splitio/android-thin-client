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
import io.split.client.thin.models.json.toPlainJsonElement

private object NullableAnyMapSerializer : KSerializer<Map<String, Any?>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("NullableAnyMap")

    override fun serialize(encoder: Encoder, value: Map<String, Any?>) {
        encoder.encodeSerializableValue(
            JsonElement.serializer(),
            buildJsonObject {
                value.forEach { (k, v) -> put(k, v.toJsonElement()) }
            }
        )
    }

    override fun deserialize(decoder: Decoder): Map<String, Any?> {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        if (element !is JsonObject) return emptyMap()
        return element.entries.associate { (k, v) -> k to v.fromJsonElement() }
    }
}

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is Boolean -> JsonPrimitive(this)
    is Number -> this.toPlainJsonElement()
    is String -> JsonPrimitive(this)
    is List<*> -> buildJsonArray {
        forEach { item -> add(item.toJsonElement()) }
    }
    is Map<*, *> -> buildJsonObject {
        forEach { (k, v) -> if (k is String) put(k, v.toJsonElement()) }
    }
    else -> JsonPrimitive(toString())
}

private fun JsonElement.fromJsonElement(): Any? = when (this) {
    is JsonNull -> null
    is JsonArray -> map { it.fromJsonElement() }
    is JsonObject -> entries.associate { (k, v) -> k to v.fromJsonElement() }
    else -> {
        val prim = jsonPrimitive
        when {
            prim.booleanOrNull != null &&
                    (prim.content == "true" || prim.content == "false") -> prim.boolean
            prim.longOrNull != null -> prim.longOrNull!!
            prim.doubleOrNull != null -> prim.doubleOrNull!!
            else -> prim.content
        }
    }
}

@Serializable
private data class TrackerEventDto(
    val trafficType: String,
    val eventType: String,
    val key: String,
    val value: Double?,
    val timestamp: Long,
    @Serializable(with = NullableAnyMapSerializer::class)
    val properties: Map<String, Any?>? = null
)

internal class TrackerEventSerializer(private val cipher: Any? = null) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun serialize(event: TrackerEvent): String {
        @Suppress("UNCHECKED_CAST")
        val dto = TrackerEventDto(
            trafficType = event.trafficType,
            eventType = event.eventType,
            key = event.key,
            value = event.value,
            timestamp = event.timestamp,
            properties = event.properties as? Map<String, Any?>
        )
        return json.encodeToString(dto)
    }

    fun deserialize(serialized: String): TrackerEvent {
        val dto = json.decodeFromString<TrackerEventDto>(serialized)
        @Suppress("UNCHECKED_CAST")
        return TrackerEvent().apply {
            trafficType = dto.trafficType
            eventType = dto.eventType
            key = dto.key
            value = dto.value
            timestamp = dto.timestamp
            properties = dto.properties as? Map<String, Any>
        }
    }
}
