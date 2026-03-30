package io.split.client.thin.internal.streaming

import io.split.android.client.backoff.ExponentialBackoffCounter
import io.split.android.client.service.sseclient.EventStreamParser
import io.split.android.client.service.sseclient.sseclient.EventSourceClientImpl
import io.split.client.thin.http.RetryableHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Container for streaming manager and its control callbacks.
 *
 * @property manager The configured streaming manager instance
 * @property startTrigger Callback to invoke when streaming should start (e.g., when targets change)
 */
data class StreamingComponents(
    val manager: DefaultStreamingManager,
    val startTrigger: () -> Unit,
)

/**
 * Creates streaming components with a configured manager and start trigger.
 *
 * The factory creates a dedicated coroutine scope for streaming operations and wires up
 * the event source client with retry/backoff logic. The returned start trigger should be
 * invoked when streaming targets change to (re)start the streaming connection.
 *
 * @param streamingUrl SSE endpoint URL
 * @param retryableHttpClient HTTP client for SSE transport
 * @param tokenProvider Lambda that returns the current JWT token (captures caller's mutable state)
 * @param onEvaluationFetchNotification Lambda called when streaming triggers a refetch
 * @return StreamingComponents containing the manager and start trigger
 */
fun createStreamingComponents(
    streamingUrl: String,
    retryableHttpClient: RetryableHttpClient,
    tokenProvider: suspend () -> String,
    onEvaluationFetchNotification: suspend () -> Unit,
): StreamingComponents {
    val streamingScope = CoroutineScope(SupervisorJob())

    val manager = DefaultStreamingManager(
        streamingUrl = streamingUrl,
        tokenProvider = tokenProvider,
        eventSourceClientProvider = {
            EventSourceClientImpl(
                StreamingTransportImpl(retryableHttpClient),
                EventStreamParser(),
            )
        },
        backoffCounterFactory = { ExponentialBackoffCounter(1, 60) },
        scope = streamingScope,
        onOccupancyZero = { /* TODO: handle occupancy zero */ },
        onEvaluationFetchNotification = onEvaluationFetchNotification,
    )

    return StreamingComponents(
        manager = manager,
        startTrigger = {
            streamingScope.launch { manager.start() }
        },
    )
}
