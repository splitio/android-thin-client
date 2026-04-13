package io.split.client.thin.internal.secure

import com.goncalossilva.murmurhash.MurmurHash3
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.nio.ByteBuffer
import java.util.Base64

internal object ContentDigest {

    fun compute(target: EvaluationTarget): String {
        val attrsJson = serializeAttributes(target.attributes)
        val input = "${target.matchingKey}:$attrsJson"
        val inputBytes = input.toByteArray(Charsets.UTF_8)
        val hash = MurmurHash3().hash128x64(inputBytes)
        val first8Bytes = ByteBuffer.allocate(8).putLong(hash[0].toLong()).array()
        return Base64.getEncoder().withoutPadding().encodeToString(first8Bytes)
    }

    internal fun serializeAttributes(attributes: Map<String, Any?>?): String {
        if (attributes.isNullOrEmpty()) return "{}"
        val jsonObject = buildJsonObject {
            attributes.keys.sorted().forEach { key ->
                put(key, toJsonPrimitive(attributes[key]))
            }
        }
        return Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), jsonObject)
    }

    private fun toJsonPrimitive(value: Any?): JsonPrimitive = when (value) {
        null -> JsonNull
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }
}
