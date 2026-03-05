package io.split.client.thin.http

import io.split.android.client.backoff.BackoffCounter
import io.split.android.client.network.HttpClient
import io.split.android.client.network.HttpException
import io.split.android.client.network.HttpResponse
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

internal class DefaultRetryableHttpClient(
    private val httpClient: HttpClient,
    private val policiesByCategory: Map<RequestCategory, CategoryRetryPolicies>,
    private val backoffFactory: (backoffBaseSeconds: Int) -> BackoffCounter,
) : RetryableHttpClient {

    override suspend fun execute(
        request: HttpRequestDescriptor,
        category: RequestCategory,
    ): HttpResponse {
        val policies = policiesByCategory[category]
            ?: throw IllegalStateException("No retry policies configured for category: $category")

        val backoffByBase = mutableMapOf<Int, BackoffCounter>()
        var attempt = 0

        while (true) {
            currentCoroutineContext().ensureActive()
            attempt++

            try {
                val response = buildRequest(request).execute()
                if (response.isSuccess) return response

                val policy = policies.policyForStatus(response.httpStatus) ?: return response
                if (!policy.shouldRetry(attempt)) return response

                delay(backoffFor(policy, backoffByBase).nextRetryTime * MILLIS_PER_SECOND)
            } catch (e: HttpException) {
                if (e.statusCode == SSL_ERROR_STATUS_CODE) throw e

                val policy = resolveExceptionPolicy(e, policies) ?: throw e
                if (!policy.shouldRetry(attempt)) throw e

                delay(backoffFor(policy, backoffByBase).nextRetryTime * MILLIS_PER_SECOND)
            }
        }
    }

    /**
     * Resolves the retry policy for an [HttpException].
     *
     * Returns null when the status is explicitly mapped to null in [CategoryRetryPolicies.byStatus],
     * meaning the caller should not retry.
     */
    private fun resolveExceptionPolicy(
        e: HttpException,
        policies: CategoryRetryPolicies,
    ): RetryPolicy? {
        val statusCode = e.statusCode
        // Explicit null in byStatus means "do not retry for this status".
        // Only fall back to default when there is no entry at all for the status.
        return if (statusCode != null && statusCode in policies.byStatus) {
            policies.policyForStatus(statusCode)
        } else {
            policies.default
        }
    }

    private fun backoffFor(
        policy: RetryPolicy,
        cache: MutableMap<Int, BackoffCounter>,
    ): BackoffCounter = cache.getOrPut(policy.backoffBaseSeconds) { backoffFactory(policy.backoffBaseSeconds) }

    private fun buildRequest(descriptor: HttpRequestDescriptor) =
        if (descriptor.body != null || descriptor.headers.isNotEmpty()) {
            httpClient.request(
                descriptor.uri,
                descriptor.method,
                descriptor.body,
                descriptor.headers,
            )
        } else {
            httpClient.request(descriptor.uri, descriptor.method)
        }

    companion object {
        private const val SSL_ERROR_STATUS_CODE = 9009
        private const val MILLIS_PER_SECOND = 1000L
    }
}
