package io.split.client.thin.internal

import io.split.android.client.backoff.ExponentialBackoffCounter
import io.split.client.thin.http.CategoryRetryPolicies
import io.split.client.thin.http.DefaultRetryableHttpClient
import io.split.client.thin.http.RequestCategory
import io.split.client.thin.http.RetryPolicy
import io.split.client.thin.http.RetryableHttpClient
import io.split.client.thin.http.contracts.HttpClient
import io.split.client.thin.internal.observer.CompositeObserver
import io.split.client.thin.internal.observer.ObservableEvent
import io.split.client.thin.internal.observer.ObservableEventType

fun createRetryableHttpClient(
    httpClient: HttpClient,
    compositeObserver: CompositeObserver,
): RetryableHttpClient {
    val defaultPolicy = RetryPolicy(maxAttempts = 3, backoffBaseSeconds = 1)
    // 4xx client errors are deterministic — retrying the same request won't change the outcome.
    // 401 in particular is handled at a higher level (DefaultSecureHttpClient re-auth flow),
    // so retrying it here would bypass that mechanism entirely.
    val noRetryStatuses = mapOf(
        400 to null, 401 to null, 403 to null, 404 to null,
        405 to null, 413 to null, 422 to null, 429 to null,
    )
    val policies = RequestCategory.entries.associateWith {
        CategoryRetryPolicies(default = defaultPolicy, byStatus = noRetryStatuses)
    }
    return DefaultRetryableHttpClient(
        httpClient = httpClient,
        policiesByCategory = policies,
        backoffFactory = { baseSeconds -> ExponentialBackoffCounter(baseSeconds) },
        onHttpRequestStarted = { request, category ->
            compositeObserver.notifyEvent(
                ObservableEvent(
                    type = ObservableEventType.HTTP_REQUEST_STARTED,
                    properties = mapOf(
                        "category" to category.name,
                        "method" to request.method.name,
                        "uri" to request.uri.toString()
                    )
                )
            )
        },
        onHttpRequestSucceeded = { response, category ->
            compositeObserver.notifyEvent(
                ObservableEvent(
                    type = ObservableEventType.HTTP_REQUEST_SUCCEEDED,
                    properties = mapOf(
                        "category" to category.name,
                        "statusCode" to response.httpStatus.toString()
                    )
                )
            )
        },
        onHttpRequestFailedRetryable = { category, statusCode, attempt ->
            compositeObserver.notifyEvent(
                ObservableEvent(
                    type = ObservableEventType.HTTP_REQUEST_FAILED_RETRYABLE,
                    properties = mapOf(
                        "category" to category.name,
                        "statusCode" to (statusCode?.toString() ?: "unknown"),
                        "attempt" to attempt.toString()
                    )
                )
            )
        },
        onHttpRequestFailedNonRetryable = { category, statusCode, error ->
            compositeObserver.notifyEvent(
                ObservableEvent(
                    type = ObservableEventType.HTTP_REQUEST_FAILED_NON_RETRYABLE,
                    properties = mapOf(
                        "category" to category.name,
                        "statusCode" to (statusCode?.toString() ?: "unknown"),
                        "error" to (error?.message ?: "unknown")
                    )
                )
            )
        },
        onHttpRetryExhausted = { category, attempts ->
            compositeObserver.notifyEvent(
                ObservableEvent(
                    type = ObservableEventType.HTTP_RETRY_EXHAUSTED,
                    properties = mapOf(
                        "category" to category.name,
                        "attempts" to attempts.toString()
                    )
                )
            )
        },
    )
}
