package io.split.client.thin.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import io.split.client.thin.models.json.toPlainJsonElement

object AnySerializer : KSerializer<Any> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Any")

    override fun serialize(encoder: Encoder, value: Any) {
        val jsonElement = when (value) {
            is String -> JsonPrimitive(value)
            is Number -> value.toPlainJsonElement()
            is Boolean -> JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
        }
        encoder.encodeSerializableValue(JsonElement.serializer(), jsonElement)
    }

    override fun deserialize(decoder: Decoder): Any {
        throw UnsupportedOperationException("Deserialization not supported")
    }
}
