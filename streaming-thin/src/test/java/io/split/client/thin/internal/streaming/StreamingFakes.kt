package io.split.client.thin.internal.streaming

import io.split.android.client.backoff.BackoffCounter
import io.split.android.client.network.HttpResponse
import io.split.android.client.service.sseclient.sseclient.EventSourceClient
import io.split.client.thin.http.HttpRequestDescriptor
import io.split.client.thin.http.RequestCategory
import io.split.client.thin.http.RetryableHttpClient
import java.net.URI

// Fake EventSourceClient for testing
class FakeEventSourceClient : EventSourceClient {
    var connectCalled = false
    var lastUri: URI? = null
    var lastHandler: EventSourceClient.EventHandler? = null
    var shouldFailConnect = false
    var shouldFailOnOpen = false
    private var currentStatus = EventSourceClient.DISCONNECTED

    override fun status(): Int = currentStatus

    override fun disconnect() {
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
    private val isClientError: Boolean = false
) : HttpResponse {
    override fun getData(): String? = data
    override fun getServerCertificates(): Array<java.security.cert.Certificate> = emptyArray()
    override fun isSuccess(): Boolean = statusCode in 200..299
    override fun isCredentialsError(): Boolean = statusCode == 401
    override fun isBadRequestError(): Boolean = statusCode == 400
    override fun isClientRelatedError(): Boolean = isClientError || statusCode in 400..499
    override fun getHttpStatus(): Int = statusCode
}
