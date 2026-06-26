package io.split.client.thin.internal.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JwtCredentialTest {

    @Test
    fun `isExpired returns true when expiresAt is in the past`() {
        val pastTimestamp = System.currentTimeMillis() / 1000 - 60
        val credential = JwtCredential(
            token = "token",
            expiresAt = pastTimestamp,
            pushEnabled = false,
        )

        assertTrue(credential.isExpired())
    }

    @Test
    fun `isExpired returns false when expiresAt is in the future`() {
        val futureTimestamp = System.currentTimeMillis() / 1000 + 3600
        val credential = JwtCredential(
            token = "token",
            expiresAt = futureTimestamp,
            pushEnabled = true,
        )

        assertFalse(credential.isExpired())
    }

    @Test
    fun `isExpired returns true when expiresAt equals current time`() {
        val nowTimestamp = System.currentTimeMillis() / 1000
        val credential = JwtCredential(
            token = "token",
            expiresAt = nowTimestamp,
            pushEnabled = false,
        )

        assertTrue(credential.isExpired())
    }
}
