package io.split.client.thin.http

data class CategoryRetryPolicies(
    val default: RetryPolicy,
    val byStatus: Map<Int, RetryPolicy?> = emptyMap(),
) {
    fun policyForStatus(status: Int): RetryPolicy? =
        if (status in byStatus) byStatus[status] else default
}
