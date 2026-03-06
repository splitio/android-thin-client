package io.split.client.thin.http

data class RetryPolicy(
    val maxAttempts: Int,
    val backoffBaseSeconds: Int,
) {
    fun shouldRetry(attempt: Int): Boolean =
        maxAttempts == UNLIMITED || attempt < maxAttempts

    companion object {
        const val UNLIMITED = -1
    }
}
