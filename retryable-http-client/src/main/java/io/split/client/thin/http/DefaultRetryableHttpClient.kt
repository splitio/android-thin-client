package io.split.client.thin.http

import io.split.android.client.backoff.BackoffCounter
import io.split.client.thin.http.contracts.HttpClient
import io.split.client.thin.http.contracts.HttpException
import io.split.client.thin.http.contracts.HttpResponse
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

internal class DefaultRetryableHttpClient(
    private val httpClient: HttpClient,
    private val policiesByCategory: Map<RequestCategory, CategoryRetryPolicies>,
    private val backoffFactory: (backoffBaseSeconds: Int) -> BackoffCounter,
    private val onHttpRequestStarted: (request: HttpRequestDescriptor, category: RequestCategory) -> Unit = { _, _ -> },
    private val onHttpRequestSucceeded: (response: HttpResponse, category: RequestCategory) -> Unit = { _, _ -> },
    private val onHttpRequestFailedRetryable: (category: RequestCategory, statusCode: Int?, attempt: Int) -> Unit = { _, _, _ -> },
    private val onHttpRequestFailedNonRetryable: (category: RequestCategory, statusCode: Int?, error: Exception?) -> Unit = { _, _, _ -> },
    private val onHttpRetryExhausted: (category: RequestCategory, attempts: Int) -> Unit = { _, _ -> },
) : RetryableHttpClient {

    override suspend fun execute(
        request: HttpRequestDescriptor,
        category: RequestCategory,
    ): HttpResponse {
        val policies = policiesByCategory[category] ?: NO_RETRY_POLICIES

        val backoffByBase = mutableMapOf<Int, BackoffCounter>()
        var attempt = 0

        while (true) {
            currentCoroutineContext().ensureActive()
            attempt++

            try {
                onHttpRequestStarted(request, category)
                val response = buildRequest(request).execute()
                if (response.isSuccess || response.httpStatus == HTTP_NOT_MODIFIED) {
                    onHttpRequestSucceeded(response, category)
                    return response
                }

                val policy = policies.policyForStatus(response.httpStatus)
                if (policy == null) {
                    onHttpRequestFailedNonRetryable(category, response.httpStatus, null)
                    return response
                }
                if (!policy.shouldRetry(attempt)) {
                    onHttpRetryExhausted(category, attempt)
                    onHttpRequestFailedNonRetryable(category, response.httpStatus, null)
                    return response
                }

                onHttpRequestFailedRetryable(category, response.httpStatus, attempt)
                delay(backoffFor(policy, backoffByBase).nextRetryTime * MILLIS_PER_SECOND)
            } catch (e: HttpException) {
                if (e.statusCode == SSL_ERROR_STATUS_CODE) {
                    onHttpRequestFailedNonRetryable(category, SSL_ERROR_STATUS_CODE, e)
                    throw e
                }

                val policy = resolveExceptionPolicy(e, policies)
                if (policy == null) {
                    onHttpRequestFailedNonRetryable(category, e.statusCode, e)
                    throw e
                }
                if (!policy.shouldRetry(attempt)) {
                    onHttpRetryExhausted(category, attempt)
                    onHttpRequestFailedNonRetryable(category, e.statusCode, e)
                    throw e
                }

                onHttpRequestFailedRetryable(category, e.statusCode, attempt)
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
        private const val SSL_ERROR_STATUS_CODE = 9009 // 9009 = NON_RETRYABLE_STATUS_CODE from HttpRequestImpl
        private const val HTTP_NOT_MODIFIED = 304
        private const val MILLIS_PER_SECOND = 1000L

        private val NO_RETRY_POLICY = RetryPolicy(maxAttempts = 1, backoffBaseSeconds = 0)
        private val NO_RETRY_POLICIES = CategoryRetryPolicies(default = NO_RETRY_POLICY)
    }
}
