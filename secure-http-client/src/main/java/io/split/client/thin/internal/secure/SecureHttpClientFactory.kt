package io.split.client.thin.internal.secure

import io.split.client.thin.http.RetryableHttpClient
import io.split.client.thin.internal.auth.AuthProvider

fun createSecureHttpClient(
    authProvider: AuthProvider<EvaluationTarget>,
    retryableHttpClient: RetryableHttpClient,
    defaultTarget: EvaluationTarget,
    evaluationsUrl: String,
    eventsUrl: String,
    telemetryUrl: String,
    sdkKey: String,
    onStreamingEmpty: (suspend () -> Unit)? = null,
): SecureHttpClient = DefaultSecureHttpClient(
    authProvider = authProvider,
    retryableHttpClient = retryableHttpClient,
    defaultTarget = defaultTarget,
    evaluationsUrl = evaluationsUrl,
    eventsUrl = eventsUrl,
    telemetryUrl = telemetryUrl,
    sdkKey = sdkKey,
    onStreamingEmpty = onStreamingEmpty,
)
