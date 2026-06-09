package io.split.client.thin.internal.auth

import android.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal class JsonTokenDeserializer(
    private val base64Decoder: (String) -> ByteArray = { input ->
        Base64.decode(input, Base64.URL_SAFE or Base64.NO_PADDING)
    },
) : TokenDeserializer {

    override fun deserialize(json: String): JwtCredential {
        val dto = format.decodeFromString<AuthResponse>(json)
        val token = dto.token ?: ""
        return JwtCredential(
            token = token,
            expiresAt = if (token.isNotEmpty()) decodeJwtExp(token) else 0L,
            pushEnabled = dto.config?.streaming?.enabled ?: false,
            connDelaySeconds = dto.config?.streaming?.delay ?: 60,
        )
    }

    private fun decodeJwtExp(token: String): Long {
        return try {
            val payload = token.split(".").getOrNull(1) ?: return Long.MAX_VALUE
            val decoded = String(base64Decoder(payload), Charsets.UTF_8)
            format.decodeFromString<JwtPayload>(decoded).exp
        } catch (e: Exception) {
            Long.MAX_VALUE
        }
    }

    private companion object {
        val format = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class JwtPayload(
    val exp: Long = Long.MAX_VALUE,
)

@Serializable
private data class AuthResponse(
    @SerialName("token") val token: String? = null,
    @SerialName("config") val config: AuthConfig? = null,
)

@Serializable
private class AuthConfig(
    @SerialName("streaming") val streaming: StreamingConfig? = null,
)

@Serializable
private class StreamingConfig(
    @SerialName("enabled") val enabled: Boolean? = null,
    @SerialName("delay") val delay: Long? = null,
)
