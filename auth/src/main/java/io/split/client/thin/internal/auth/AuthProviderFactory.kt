package io.split.client.thin.internal.auth

import io.split.client.thin.http.RetryableHttpClient
import io.split.client.thin.internal.observer.CompositeObserver
import io.split.client.thin.internal.observer.ObservableEvent
import io.split.client.thin.internal.observer.ObservableEventType

fun <T : AuthParamsProvider> createAuthProvider(
    retryableHttpClient: RetryableHttpClient,
    sdkKey: String,
    authUrl: String,
    compositeObserver: CompositeObserver,
    compositeKeyBuilder: (Set<T>) -> T,
): AuthProvider<T> {
    val storage = InMemoryCredentialStorage<T>()
    val fetcher = DefaultCredentialFetcher<T>(
        retryableHttpClient = retryableHttpClient,
        sdkKey = sdkKey,
        serviceUrl = authUrl,
        onJwtFetchStarted = { target ->
            compositeObserver.notifyEvent(
                ObservableEvent(
                    type = ObservableEventType.JWT_FETCH_STARTED,
                    properties = mapOf("matchingKey" to extractMatchingKey(target))
                )
            )
        },
        onJwtFetchSucceeded = { credential, target ->
            compositeObserver.notifyEvent(
                ObservableEvent(
                    type = ObservableEventType.JWT_FETCH_SUCCEEDED,
                    properties = mapOf(
                        "matchingKey" to extractMatchingKey(target),
                        "expiresAt" to credential.expiresAt.toString(),
                        "pushEnabled" to credential.pushEnabled.toString()
                    )
                )
            )
        },
        onJwtFetchFailedNonRetryable = { target, error ->
            compositeObserver.notifyEvent(
                ObservableEvent(
                    type = ObservableEventType.JWT_FETCH_FAILED_NON_RETRYABLE,
                    properties = mapOf(
                        "matchingKey" to extractMatchingKey(target),
                        "error" to error.message.orEmpty()
                    )
                )
            )
        },
    )
    return DefaultAuthProvider(
        credentialFetcher = fetcher,
        credentialStorage = storage,
        compositeKeyBuilder = compositeKeyBuilder,
        onJwtRequestStarted = { target ->
            compositeObserver.notifyEvent(
                ObservableEvent(
                    type = ObservableEventType.JWT_REQUEST_STARTED,
                    properties = mapOf("matchingKey" to extractMatchingKey(target))
                )
            )
        },
        onJwtReturnedFromStorage = { credential, target ->
            compositeObserver.notifyEvent(
                ObservableEvent(
                    type = ObservableEventType.JWT_RETURNED_FROM_STORAGE,
                    properties = mapOf(
                        "matchingKey" to extractMatchingKey(target),
                        "expiresAt" to credential.expiresAt.toString()
                    )
                )
            )
        },
        onJwtExpiredOrInvalid = { target ->
            compositeObserver.notifyEvent(
                ObservableEvent(
                    type = ObservableEventType.JWT_EXPIRED_OR_INVALID,
                    properties = mapOf("matchingKey" to extractMatchingKey(target))
                )
            )
        },
        onJwtStored = { credential, target ->
            compositeObserver.notifyEvent(
                ObservableEvent(
                    type = ObservableEventType.JWT_STORED,
                    properties = mapOf(
                        "matchingKey" to extractMatchingKey(target),
                        "expiresAt" to credential.expiresAt.toString()
                    )
                )
            )
        },
    )
}

private fun <T : AuthParamsProvider> extractMatchingKey(target: T): String =
    target.getUsers()
