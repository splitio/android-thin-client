package io.split.client.thin.models.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Custom serializer for Double values that ensures plain decimal notation is used
 * instead of scientific notation.
 *
 * This serializer delegates to [Number.toPlainJsonElement] to ensure consistent
 * handling of double values across the codebase. Non-finite values (NaN, Infinity)
 * are serialized as JSON null.
 *
 * Note: JSON `null` (and any value the serializer wrote for a non-finite Double) decodes
 * back to [Double.NaN]; non-finite values do not round-trip.
 *
 * Usage:
 * ```
 * @Serializable
 * data class MyData(
 *     @Serializable(with = PlainDoubleSerializer::class)
 *     val value: Double
 * )
 * ```
 */
object PlainDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("PlainDouble", PrimitiveKind.DOUBLE)

    override fun serialize(encoder: Encoder, value: Double) {
        if (encoder is JsonEncoder) {
            encoder.encodeJsonElement(value.toPlainJsonElement())
        } else {
            // Non-JSON formats can't carry an unquoted literal; emit the raw double.
            encoder.encodeDouble(value)
        }
    }

    override fun deserialize(decoder: Decoder): Double {
        require(decoder is JsonDecoder) { "PlainDoubleSerializer only works with JSON decoding" }
        val element = decoder.decodeJsonElement()
        return if (element is JsonNull) {
            Double.NaN
        } else {
            element.jsonPrimitive.content.toDouble()
        }
    }
}
