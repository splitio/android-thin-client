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
            expiresAt = if (token.isNotEmpty()) decodeJwtExp(token) else Long.MAX_VALUE,
            pushEnabled = dto.pushEnabled ?: true,
            connDelaySeconds = dto.connDelay ?: 60,
        )
    }

    private fun decodeJwtExp(token: String): Long {
        return try {
            val payload = token.split(".").getOrNull(1) ?: return Long.MAX_VALUE
            val decoded = String(base64Decoder(payload), Charsets.UTF_8)
            Regex("\"exp\":(\\d+)").find(decoded)?.groupValues?.get(1)?.toLongOrNull() ?: Long.MAX_VALUE
        } catch (e: Exception) {
            Long.MAX_VALUE
        }
    }

    private companion object {
        val format = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class AuthResponse(
    @SerialName("token") val token: String? = null,
    @SerialName("pushEnabled") val pushEnabled: Boolean? = null,
    @SerialName("connDelay") val connDelay: Long? = null,
)
