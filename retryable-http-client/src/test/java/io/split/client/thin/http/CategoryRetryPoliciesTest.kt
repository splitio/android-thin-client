package io.split.client.thin.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryRetryPoliciesTest {

    private val defaultPolicy = RetryPolicy(maxAttempts = 3, backoffBaseSeconds = 1)

    @Test
    fun `policyForStatus returns status-specific policy when one exists`() {
        val specificPolicy = RetryPolicy(maxAttempts = 5, backoffBaseSeconds = 2)
        val policies = CategoryRetryPolicies(
            default = defaultPolicy,
            byStatus = mapOf(429 to specificPolicy),
        )

        assertEquals(specificPolicy, policies.policyForStatus(429))
    }

    @Test
    fun `policyForStatus returns default policy when no status-specific entry exists`() {
        val policies = CategoryRetryPolicies(
            default = defaultPolicy,
            byStatus = mapOf(429 to RetryPolicy(maxAttempts = 5, backoffBaseSeconds = 2)),
        )

        assertEquals(defaultPolicy, policies.policyForStatus(500))
    }

    @Test
    fun `policyForStatus returns null when status maps to null`() {
        val policies = CategoryRetryPolicies(
            default = defaultPolicy,
            byStatus = mapOf(404 to null),
        )

        assertNull(policies.policyForStatus(404))
    }

    @Test
    fun `policyForStatus returns default when byStatus is empty`() {
        val policies = CategoryRetryPolicies(
            default = defaultPolicy,
            byStatus = emptyMap(),
        )

        assertEquals(defaultPolicy, policies.policyForStatus(500))
    }
}
