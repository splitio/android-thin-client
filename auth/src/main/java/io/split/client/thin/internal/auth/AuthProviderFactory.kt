package io.split.client.thin.internal.auth

import io.split.client.thin.http.RetryableHttpClient
import io.split.client.thin.internal.observer.CompositeObserver
import io.split.client.thin.internal.observer.ObservableEvent
import io.split.client.thin.internal.observer.ObservableEventType

fun createAuthProvider(
    retryableHttpClient: RetryableHttpClient,
    sdkKey: String,
    authUrl: String,
    compositeObserver: CompositeObserver,
    defaultTarget: String? = null,
    onUnauthorized: (target: String) -> Unit = {},
): AuthProvider {
    val storage = InMemoryCredentialStorage()
    val fetcher = DefaultCredentialFetcher(
        retryableHttpClient = retryableHttpClient,
        sdkKey = sdkKey,
        serviceUrl = authUrl,
        onJwtFetchStarted = { target ->
            compositeObserver.notifyEvent(
                ObservableEvent(
                    type = ObservableEventType.JWT_FETCH_STARTED,
                    properties = mapOf("matchingKey" to target)
                )
            )
        },
        onJwtFetchSucceeded = { credential, target ->
            compositeObserver.notifyEvent(
                ObservableEvent(
                    type = ObservableEventType.JWT_FETCH_SUCCEEDED,
                    properties = mapOf(
                        "matchingKey" to target,
                        "expiresAt" to credential.expiresAt.toString(),
                        "pushEnabled" to credential.pushEnabled.toString(),
                        "connDelaySeconds" to credential.connDelaySeconds.toString()
                    )
                )
            )
        },
        onJwtFetchFailedNonRetryable = { target, error ->
            compositeObserver.notifyEvent(
                ObservableEvent(
                    type = ObservableEventType.JWT_FETCH_FAILED_NON_RETRYABLE,
                    properties = mapOf(
                        "matchingKey" to target,
                        "error" to error.message.orEmpty()
                    )
                )
            )
        },
        onUnauthorized = onUnauthorized,
    )
    return DefaultAuthProvider(
        credentialFetcher = fetcher,
        credentialStorage = storage,
        defaultTarget = defaultTarget,
        onJwtRequestStarted = { target ->
            compositeObserver.notifyEvent(
                ObservableEvent(
                    type = ObservableEventType.JWT_REQUEST_STARTED,
                    properties = mapOf("matchingKey" to target)
                )
            )
        },
        onJwtReturnedFromStorage = { credential, target ->
            compositeObserver.notifyEvent(
                ObservableEvent(
                    type = ObservableEventType.JWT_RETURNED_FROM_STORAGE,
                    properties = mapOf(
                        "matchingKey" to target,
                        "expiresAt" to credential.expiresAt.toString()
                    )
                )
            )
        },
        onJwtExpiredOrInvalid = { target ->
            compositeObserver.notifyEvent(
                ObservableEvent(
                    type = ObservableEventType.JWT_EXPIRED_OR_INVALID,
                    properties = mapOf("matchingKey" to target)
                )
            )
        },
        onJwtStored = { credential, target ->
            compositeObserver.notifyEvent(
                ObservableEvent(
                    type = ObservableEventType.JWT_STORED,
                    properties = mapOf(
                        "matchingKey" to target,
                        "expiresAt" to credential.expiresAt.toString()
                    )
                )
            )
        },
    )
}
