package io.split.client.thin.internal.auth

internal data class JwtCredential(
    val token: String,
    val expiresAt: Long,     // Unix timestamp (seconds)
    val pushEnabled: Boolean,
) {

    fun isExpired(): Boolean = System.currentTimeMillis() / 1000 >= expiresAt
}
