package io.split.client.thin.internal

import io.split.client.thin.http.HttpRequestDescriptor
import io.split.client.thin.http.RequestCategory
import io.split.client.thin.http.RetryableHttpClient
import io.split.client.thin.http.contracts.HttpClient
import io.split.client.thin.http.contracts.HttpMethod
import io.split.client.thin.http.contracts.HttpRequest
import io.split.client.thin.http.contracts.HttpResponse
import io.split.client.thin.internal.observer.CompositeObserver
import io.split.client.thin.internal.observer.ObservableEvent
import io.split.client.thin.internal.observer.ObservableEventType
import io.split.client.thin.internal.observer.Observer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class RetryableHttpClientFactoryTest {

    private val capturedEvents = mutableListOf<ObservableEvent>()
    private val fakeObserver = object : CompositeObserver {
        override fun notifyEvent(event: ObservableEvent) { capturedEvents.add(event) }
        override fun register(observer: Observer) {}
        override fun unregisterAll() {}
    }

    @Test
    fun `createRetryableHttpClient returns a RetryableHttpClient`() {
        val httpClient = FakeHttpClient(200)
        val result = createRetryableHttpClient(httpClient, fakeObserver)
        assertNotNull(result)
        assertTrue(result is RetryableHttpClient)
    }

    @Test
    fun `observer receives HTTP_REQUEST_STARTED on execute`() = runTest {
        val httpClient = FakeHttpClient(200)
        val client = createRetryableHttpClient(httpClient, fakeObserver)

        client.execute(
            HttpRequestDescriptor(URI.create("https://api.split.io"), HttpMethod.GET),
            RequestCategory.EVALUATIONS
        )

        assertTrue(capturedEvents.any { it.type == ObservableEventType.HTTP_REQUEST_STARTED })
    }

    @Test
    fun `observer receives HTTP_REQUEST_SUCCEEDED on success`() = runTest {
        val httpClient = FakeHttpClient(200)
        val client = createRetryableHttpClient(httpClient, fakeObserver)

        client.execute(
            HttpRequestDescriptor(URI.create("https://api.split.io"), HttpMethod.GET),
            RequestCategory.EVALUATIONS
        )

        assertTrue(capturedEvents.any { it.type == ObservableEventType.HTTP_REQUEST_SUCCEEDED })
    }

    @Test
    fun `observer receives HTTP_REQUEST_FAILED_NON_RETRYABLE on 401`() = runTest {
        val httpClient = FakeHttpClient(401)
        val client = createRetryableHttpClient(httpClient, fakeObserver)

        client.execute(
            HttpRequestDescriptor(URI.create("https://api.split.io"), HttpMethod.GET),
            RequestCategory.EVALUATIONS
        )

        assertTrue(capturedEvents.any { it.type == ObservableEventType.HTTP_REQUEST_FAILED_NON_RETRYABLE })
        assertEquals("401", capturedEvents.first { it.type == ObservableEventType.HTTP_REQUEST_FAILED_NON_RETRYABLE }.properties["statusCode"])
    }

    @Test
    fun `observer receives HTTP_REQUEST_FAILED_RETRYABLE on 500 before retry`() = runTest {
        val httpClient = FakeHttpClient(500, thenStatusCode = 200)
        val client = createRetryableHttpClient(httpClient, fakeObserver)

        client.execute(
            HttpRequestDescriptor(URI.create("https://api.split.io"), HttpMethod.GET),
            RequestCategory.EVALUATIONS
        )

        assertTrue(capturedEvents.any { it.type == ObservableEventType.HTTP_REQUEST_FAILED_RETRYABLE })
    }

    @Test
    fun `observer receives HTTP_RETRY_EXHAUSTED when all attempts fail`() = runTest {
        val httpClient = FakeHttpClient(500)
        val client = createRetryableHttpClient(httpClient, fakeObserver)

        client.execute(
            HttpRequestDescriptor(URI.create("https://api.split.io"), HttpMethod.GET),
            RequestCategory.EVALUATIONS
        )

        assertTrue(capturedEvents.any { it.type == ObservableEventType.HTTP_RETRY_EXHAUSTED })
    }

    private class FakeHttpClient(private val statusCode: Int, private val thenStatusCode: Int? = null) : HttpClient {
        private var callCount = 0
        override fun request(uri: URI, method: HttpMethod): HttpRequest {
            val code = if (thenStatusCode != null && callCount++ > 0) thenStatusCode else statusCode
            return FakeHttpRequest(code)
        }
        override fun request(uri: URI, method: HttpMethod, body: String?, headers: Map<String, String>): HttpRequest = FakeHttpRequest(statusCode)
    }

    private class FakeHttpRequest(private val statusCode: Int) : HttpRequest {
        override fun execute(): HttpResponse = object : HttpResponse {
            override val isSuccess: Boolean = statusCode in 200..299
            override val httpStatus: Int = statusCode
            override fun getData(): String? = null
        }
    }
}
