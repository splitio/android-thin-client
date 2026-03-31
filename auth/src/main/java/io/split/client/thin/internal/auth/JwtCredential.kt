package io.split.client.thin.internal.auth

data class JwtCredential(
    val token: String,
    val expiresAt: Long,     // Unix timestamp (seconds)
    val pushEnabled: Boolean,
    val connDelaySeconds: Long = 0,
) {

    fun isExpired(): Boolean = System.currentTimeMillis() / 1000 >= expiresAt
}
