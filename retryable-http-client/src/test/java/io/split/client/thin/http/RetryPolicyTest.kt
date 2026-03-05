package io.split.client.thin.http

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPolicyTest {

    @Test
    fun `shouldRetry returns true when attempt is less than maxAttempts`() {
        val policy = RetryPolicy(maxAttempts = 3, backoffBaseSeconds = 1)

        assertTrue(policy.shouldRetry(1))
        assertTrue(policy.shouldRetry(2))
    }

    @Test
    fun `shouldRetry returns false when attempt equals maxAttempts`() {
        val policy = RetryPolicy(maxAttempts = 3, backoffBaseSeconds = 1)

        assertFalse(policy.shouldRetry(3))
    }

    @Test
    fun `shouldRetry returns false when attempt exceeds maxAttempts`() {
        val policy = RetryPolicy(maxAttempts = 2, backoffBaseSeconds = 1)

        assertFalse(policy.shouldRetry(3))
    }

    @Test
    fun `shouldRetry always returns true when maxAttempts is unlimited`() {
        val policy = RetryPolicy(maxAttempts = -1, backoffBaseSeconds = 1)

        assertTrue(policy.shouldRetry(1))
        assertTrue(policy.shouldRetry(100))
        assertTrue(policy.shouldRetry(Int.MAX_VALUE))
    }

    @Test
    fun `shouldRetry returns false for single attempt policy`() {
        val policy = RetryPolicy(maxAttempts = 1, backoffBaseSeconds = 1)

        assertFalse(policy.shouldRetry(1))
    }
}
