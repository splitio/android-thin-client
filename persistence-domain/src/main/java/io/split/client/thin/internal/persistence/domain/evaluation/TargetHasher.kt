package io.split.client.thin.internal.persistence.domain.evaluation

import com.goncalossilva.murmurhash.MurmurHash3
import io.split.client.thin.internal.evaluation.EvaluationKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import io.split.client.thin.models.json.toPlainJsonElement

internal data class HashedTarget(val keyHash: String, val attrsHash: String)

internal class TargetHasher {

    private val hasher = MurmurHash3()

    private fun toJsonElement(value: Any?): JsonElement = when (value) {
        null -> throw IllegalArgumentException("null values should not reach here")
        is Boolean -> JsonPrimitive(value)
        is Number -> value.toPlainJsonElement()
        is String -> JsonPrimitive(value)
        is List<*> -> buildJsonArray {
            value.forEach { item -> if (item != null) add(toJsonElement(item)) }
        }
        is Map<*, *> -> buildJsonObject {
            value.forEach { (k, v) -> if (k is String && v != null) put(k, toJsonElement(v)) }
        }
        else -> JsonPrimitive(value.toString())
    }

    fun hash(evalKey: EvaluationKey): HashedTarget {
        // JSON-encode the key pair to avoid separator collision
        val keyInput = Json.encodeToString(
            kotlinx.serialization.json.JsonArray.serializer(),
            buildJsonArray {
                add(JsonPrimitive(evalKey.key.matchingKey))
                add(JsonPrimitive(evalKey.key.bucketingKey))
            }
        )
        // JSON-encode sorted attrs to avoid separator collision in keys/values
        val attrsInput = if (evalKey.attributes.isEmpty()) {
            "{}"
        } else {
            val jsonObject = buildJsonObject {
                evalKey.attributes.entries.sortedBy { it.key }.forEach { (k, v) ->
                    if (v != null) put(k, toJsonElement(v))
                }
            }
            Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), jsonObject)
        }
        val keyHashArr = hasher.hash128x86(keyInput.toByteArray(Charsets.UTF_8))
        val attrsHashArr = hasher.hash128x86(attrsInput.toByteArray(Charsets.UTF_8))
        return HashedTarget(
            keyHash = "%08x%08x".format(keyHashArr[0].toLong() and 0xFFFFFFFFL, keyHashArr[1].toLong() and 0xFFFFFFFFL),
            attrsHash = "%08x%08x".format(attrsHashArr[0].toLong() and 0xFFFFFFFFL, attrsHashArr[1].toLong() and 0xFFFFFFFFL)
        )
    }
}
