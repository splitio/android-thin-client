package io.split.client.thin.internal.streaming

import android.util.Base64
import io.split.android.client.utils.logger.Logger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

private const val PUBLISHERS_CHANNEL_METADATA = "channel-metadata:publishers"
private const val PUBLISHERS_CHANNEL_PREFIX = "[?occupancy=metrics.publishers]"

internal class SseJwtParser(
    private val base64Decoder: (String) -> String? = { encoded ->
        try {
            Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_PADDING).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    },
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    fun parse(rawToken: String): List<String> {
        val parts = rawToken.split(".")
        if (parts.size < 2) {
            Logger.e("SseJwtParser: invalid JWT format")
            return emptyList()
        }

        val payload = base64Decoder(parts[1])
        if (payload == null) {
            Logger.e("SseJwtParser: failed to decode JWT payload")
            return emptyList()
        }

        return try {
            val dto = json.decodeFromString<JwtPayloadDto>(payload)
            val capabilityJson = dto.ably ?: return emptyList()
            val capabilityMap = json.decodeFromString<JsonObject>(capabilityJson)

            capabilityMap.keys.map { channel ->
                val permissions = capabilityMap[channel]?.jsonArray
                    ?.map { it.jsonPrimitive.content }
                    ?: emptyList()
                if (permissions.contains(PUBLISHERS_CHANNEL_METADATA)) {
                    "$PUBLISHERS_CHANNEL_PREFIX$channel"
                } else {
                    channel
                }
            }
        } catch (e: Exception) {
            Logger.e("SseJwtParser: failed to parse JWT payload: ${e.message}")
            emptyList()
        }
    }
}

@Serializable
private data class JwtPayloadDto(
    @SerialName("x-ably-capability") val ably: String? = null
)
