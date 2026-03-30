package io.split.client.thin.internal.persistence.domain.evaluation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal class AttributesSerializer(private val cipher: Any? = null) {

    private val json = Json

    fun serialize(attributes: Map<String, Any?>): String {
        val jsonObject = buildJsonObject {
            attributes.forEach { (key, value) ->
                when (value) {
                    null -> put(key, JsonNull)
                    is String -> put(key, JsonPrimitive(value))
                    is Boolean -> put(key, JsonPrimitive(value))
                    is Number -> put(key, JsonPrimitive(value))
                    else -> put(key, JsonPrimitive(value.toString()))
                }
            }
        }
        return json.encodeToString(JsonObject.serializer(), jsonObject)
    }

    fun deserialize(serialized: String): Map<String, Any?> {
        val jsonObject = json.decodeFromString(JsonObject.serializer(), serialized)
        return jsonObject.mapValues { (_, element) ->
            when {
                element is JsonNull -> null
                element.jsonPrimitive.booleanOrNull != null &&
                        (element.jsonPrimitive.content == "true" || element.jsonPrimitive.content == "false") ->
                    element.jsonPrimitive.boolean
                element.jsonPrimitive.longOrNull != null -> element.jsonPrimitive.longOrNull
                element.jsonPrimitive.doubleOrNull != null -> element.jsonPrimitive.doubleOrNull
                else -> element.jsonPrimitive.content
            }
        }
    }
}
