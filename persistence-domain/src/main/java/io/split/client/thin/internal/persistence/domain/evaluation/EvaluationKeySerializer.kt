package io.split.client.thin.internal.persistence.domain.evaluation

import io.split.client.thin.Key
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class KeyDto(val matchingKey: String, val bucketingKey: String? = null)

internal class EvaluationKeySerializer(private val cipher: Any? = null) {

    private val json = Json { ignoreUnknownKeys = true }

    fun serialize(key: Key): String {
        val dto = KeyDto(matchingKey = key.matchingKey, bucketingKey = key.bucketingKey)
        return json.encodeToString(dto)
    }

    fun deserialize(serialized: String): Key {
        val dto = json.decodeFromString<KeyDto>(serialized)
        return Key(matchingKey = dto.matchingKey, bucketingKey = dto.bucketingKey)
    }
}
