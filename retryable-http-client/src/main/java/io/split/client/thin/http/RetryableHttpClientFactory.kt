package io.split.client.thin.http

import io.split.android.client.backoff.ExponentialBackoffCounter
import io.split.android.client.network.HttpClient

fun createRetryableHttpClient(httpClient: HttpClient): RetryableHttpClient {
    val defaultPolicy = RetryPolicy(maxAttempts = 3, backoffBaseSeconds = 1)
    val policies = RequestCategory.entries.associateWith { CategoryRetryPolicies(default = defaultPolicy) }
    return DefaultRetryableHttpClient(
        httpClient = httpClient,
        policiesByCategory = policies,
        backoffFactory = { baseSeconds -> ExponentialBackoffCounter(baseSeconds) },
    )
}
