package io.split.client.thin.internal.streaming

import io.split.android.client.backoff.BackoffCounter
import io.split.android.client.network.HttpClient
import io.split.android.client.network.HttpMethod
import io.split.android.client.network.HttpRequest
import io.split.android.client.network.HttpStreamRequest
import io.split.android.client.network.HttpStreamResponse
import io.split.android.client.service.sseclient.sseclient.EventSourceClient
import io.split.client.thin.http.HttpRequestDescriptor
import io.split.client.thin.http.RequestCategory
import io.split.client.thin.http.RetryableHttpClient
import io.split.client.thin.http.contracts.HttpResponse
import io.split.client.thin.internal.observer.CompositeObserver
import io.split.client.thin.internal.observer.ObservableEvent
import io.split.client.thin.internal.observer.Observer
import java.io.BufferedReader
import java.net.URI

// Fake EventSourceClient for testing
class FakeEventSourceClient : EventSourceClient {
    var connectCalled = false
    var lastUri: URI? = null
    var lastHandler: EventSourceClient.EventHandler? = null
    var shouldFailConnect = false
    var shouldFailOnOpen = false
    var disconnectCalled = false
    private var currentStatus = EventSourceClient.DISCONNECTED

    override fun status(): Int = currentStatus

    override fun disconnect() {
        disconnectCalled = true
        currentStatus = EventSourceClient.DISCONNECTED
    }

    override fun connect(uri: URI, handler: EventSourceClient.EventHandler) {
        connectCalled = true
        lastUri = uri
        lastHandler = handler
        currentStatus = EventSourceClient.CONNECTING

        if (shouldFailConnect) {
            currentStatus = EventSourceClient.DISCONNECTED
            throw RuntimeException("Connection failed")
        }

        currentStatus = EventSourceClient.CONNECTED
        if (!shouldFailOnOpen) {
            handler.onOpen()
        }
    }

    fun simulateMessage(event: Map<String, String>) {
        lastHandler?.onMessage(event)
    }

    fun simulateError(retryable: Boolean = true) {
        lastHandler?.onError(retryable)
    }
}

// Fake CompositeObserver for testing
class FakeCompositeObserver : CompositeObserver {
    val events = mutableListOf<ObservableEvent>()
    override fun notifyEvent(event: ObservableEvent) { events.add(event) }
    override fun register(observer: Observer) {}
    override fun unregister(observer: Observer) {}
    override fun unregisterAll() {}
}

// Fake BackoffCounter for predictable delays
class FakeBackoffCounter(private val delays: List<Long> = listOf(100, 200, 400)) : BackoffCounter {
    private var index = 0
    var resetCount = 0

    override fun getNextRetryTime(): Long {
        val delay = if (index < delays.size) delays[index] else delays.last()
        index++
        return delay
    }

    override fun resetCounter() {
        index = 0
        resetCount++
    }
}

// Fake RetryableHttpClient for testing
class FakeRetryableHttpClient(
    private val responseProvider: (HttpRequestDescriptor, RequestCategory) -> HttpResponse = { _, _ ->
        FakeHttpResponse(200, "")
    }
) : RetryableHttpClient {
    override suspend fun execute(request: HttpRequestDescriptor, category: RequestCategory): HttpResponse {
        return responseProvider(request, category)
    }
}

// Fake HttpResponse for testing
class FakeHttpResponse(
    private val statusCode: Int,
    private val data: String?,
) : HttpResponse {
    override val isSuccess: Boolean = statusCode in 200..299
    override val httpStatus: Int = statusCode
    override fun getData(): String? = data
}

// Fake HttpStreamResponse for testing streaming transport
class FakeHttpStreamResponse(
    private val statusCode: Int,
    private val bufferedReader: BufferedReader? = null,
    private val isClientError: Boolean = false,
) : HttpStreamResponse {
    var closeCalled = false
        private set

    override fun getBufferedReader(): BufferedReader? = bufferedReader
    override fun isSuccess(): Boolean = statusCode in 200..299
    override fun isCredentialsError(): Boolean = statusCode == 401
    override fun isBadRequestError(): Boolean = statusCode == 400
    override fun isClientRelatedError(): Boolean = isClientError || statusCode in 400..499
    override fun getHttpStatus(): Int = statusCode
    override fun close() { closeCalled = true }
}

// Fake HttpStreamRequest for testing streaming transport
class FakeHttpStreamRequest(
    private val response: FakeHttpStreamResponse,
) : HttpStreamRequest {
    val headers = mutableMapOf<String, String>()
    var closeCalled = false
        private set

    override fun addHeader(name: String, value: String) { headers[name] = value }
    override fun execute(): HttpStreamResponse = response
    override fun close() { closeCalled = true }
}

// Fake HttpClient for testing streaming transport
class FakeHttpClient(
    private val streamRequestProvider: (URI) -> HttpStreamRequest = { _ ->
        FakeHttpStreamRequest(FakeHttpStreamResponse(200))
    }
) : HttpClient {
    var lastStreamRequestUri: URI? = null
        private set

    override fun setHeader(name: String, value: String) {}
    override fun addHeaders(headers: MutableMap<String, String>) {}
    override fun setStreamingHeader(name: String, value: String) {}
    override fun addStreamingHeaders(headers: MutableMap<String, String>) {}
    override fun request(uri: URI, httpMethod: HttpMethod): HttpRequest = throw UnsupportedOperationException()
    override fun request(uri: URI, requestMethod: HttpMethod, body: String, headers: MutableMap<String, String>): HttpRequest = throw UnsupportedOperationException()
    override fun request(uri: URI, httpMethod: HttpMethod, body: String): HttpRequest = throw UnsupportedOperationException()
    override fun streamRequest(uri: URI): HttpStreamRequest {
        lastStreamRequestUri = uri
        return streamRequestProvider(uri)
    }
    override fun close() {}
}
