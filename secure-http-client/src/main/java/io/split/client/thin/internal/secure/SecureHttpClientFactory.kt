package io.split.client.thin.internal.secure

import io.split.client.thin.http.RetryableHttpClient
import io.split.client.thin.internal.auth.AuthProvider

fun createSecureHttpClient(
    authProvider: AuthProvider,
    retryableHttpClient: RetryableHttpClient,
    evaluationsUrl: String,
    eventsUrl: String,
    telemetryUrl: String,
    sdkKey: String,
): SecureHttpClient = DefaultSecureHttpClient(
    authProvider = authProvider,
    retryableHttpClient = retryableHttpClient,
    evaluationsUrl = evaluationsUrl,
    eventsUrl = eventsUrl,
    telemetryUrl = telemetryUrl,
    sdkKey = sdkKey,
)
