package io.split.client.thin.http

import io.split.android.client.backoff.ExponentialBackoffCounter
import io.split.android.client.network.HttpClient
import io.split.client.thin.internal.observer.CompositeObserver
import io.split.client.thin.internal.observer.ObservableEvent
import io.split.client.thin.internal.observer.ObservableEventType

fun createRetryableHttpClient(
    httpClient: HttpClient,
    compositeObserver: CompositeObserver,
): RetryableHttpClient {
    val defaultPolicy = RetryPolicy(maxAttempts = 3, backoffBaseSeconds = 1)
    val policies = RequestCategory.entries.associateWith { CategoryRetryPolicies(default = defaultPolicy) }
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
