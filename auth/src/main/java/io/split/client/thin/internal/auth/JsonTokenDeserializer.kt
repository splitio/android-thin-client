package io.split.client.thin.internal.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal class JsonTokenDeserializer : TokenDeserializer {

    override fun deserialize(json: String): JwtCredential {
        val dto = format.decodeFromString<AuthResponse>(json)
        return JwtCredential(
            token = dto.token,
            expiresAt = dto.expiresAt,
            pushEnabled = dto.pushEnabled,
        )
    }

    private companion object {
        val format = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
internal data class AuthResponse(
    @SerialName("token") val token: String,
    @SerialName("expiresAt") val expiresAt: Long,
    @SerialName("pushEnabled") val pushEnabled: Boolean,
)
