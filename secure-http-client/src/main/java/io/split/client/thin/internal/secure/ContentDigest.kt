package io.split.client.thin.internal.secure

import com.goncalossilva.murmurhash.MurmurHash3
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.nio.ByteBuffer
import java.util.Base64

internal object ContentDigest {

    fun compute(target: EvaluationTarget): String {
        val attrsJson = serializeAttributes(target.attributes)
        val input = "${target.matchingKey}:$attrsJson"
        val inputBytes = input.toByteArray(Charsets.UTF_8)
        val hash = MurmurHash3().hash128x86(inputBytes)
        val first8Bytes = ByteBuffer.allocate(8).putInt(hash[0].toInt()).putInt(hash[1].toInt()).array()
        return Base64.getEncoder().withoutPadding().encodeToString(first8Bytes)
    }

    internal fun serializeAttributes(attributes: Map<String, Any?>?): String {
        val nonNull = attributes?.filterValues { it != null } ?: emptyMap()
        if (nonNull.isEmpty()) return "{}"
        val jsonObject = buildJsonObject {
            nonNull.keys.sorted().forEach { key ->
                put(key, toJsonElement(nonNull[key]!!))
            }
        }
        return Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), jsonObject)
    }

    private fun toJsonElement(value: Any): JsonElement = when (value) {
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is List<*> -> buildJsonArray { value.filterNotNull().forEach { add(toJsonElement(it)) } }
        else -> JsonPrimitive(value.toString())
    }
}
