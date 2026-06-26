package io.split.client.thin.internal.streaming

import io.split.android.client.backoff.ExponentialBackoffCounter
import io.split.android.client.service.sseclient.EventStreamParser
import io.split.android.client.service.sseclient.sseclient.EventSourceClientImpl
import io.split.android.client.network.HttpClient
import io.split.client.thin.internal.observer.CompositeObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

data class StreamingComponents(
    val manager: StreamingManager,
    val startTrigger: () -> Unit,
)

fun createStreamingComponents(
    streamingUrl: String,
    httpClient: HttpClient,
    parentScope: CoroutineScope,
    tokenProvider: suspend () -> StreamingToken,
    onEvaluationFetchNotification: suspend (EvaluationUpdateNotification?) -> Unit,
    onPushDisabled: suspend () -> Unit = {},
    onPushEnabled: suspend () -> Unit = {},
    invalidateToken: suspend () -> Unit = {},
    observer: CompositeObserver,
    evalChangeNumberHolder: AtomicLong = AtomicLong(Long.MIN_VALUE),
): StreamingComponents {
    val streamingScope = CoroutineScope(SupervisorJob(parentScope.coroutineContext[kotlinx.coroutines.Job]))

    val manager = DefaultStreamingManager(
        streamingUrl = streamingUrl,
        tokenProvider = tokenProvider,
        eventSourceClientProvider = {
            EventSourceClientImpl(
                StreamingTransportImpl(httpClient),
                EventStreamParser(),
            )
        },
        backoffCounterFactory = { ExponentialBackoffCounter(1, 60) },
        scope = streamingScope,
        onEvaluationFetchNotification = onEvaluationFetchNotification,
        onPushDisabled = onPushDisabled,
        onPushEnabled = onPushEnabled,
        invalidateToken = invalidateToken,
        observer = observer,
        evalChangeNumberHolder = evalChangeNumberHolder,
    )

    return StreamingComponents(
        manager = manager,
        startTrigger = {
            streamingScope.launch { manager.start() }
        },
    )
}
