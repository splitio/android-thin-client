package io.split.client.thin.internal.streaming

import io.split.android.client.network.HttpMethod
import io.split.android.client.network.HttpResponse
import io.split.client.thin.http.HttpRequestDescriptor
import io.split.client.thin.http.RequestCategory
import io.split.client.thin.http.RetryableHttpClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.BufferedReader
import java.net.URI

class StreamingTransportImplTest {

    @Test
    fun `connect creates StreamingConnection with correct URI`() {
        val client = FakeRetryableHttpClient()
        val transport = StreamingTransportImpl(client)
        val uri = URI("https://streaming.split.io/sse?token=abc")

        val connection = transport.connect(uri)

        assertNotNull(connection)
    }

    @Test
    fun `execute makes HTTP GET request with SSE headers`() = runBlocking {
        var capturedRequest: HttpRequestDescriptor? = null
        var capturedCategory: RequestCategory? = null
        val client = FakeRetryableHttpClient { request, category ->
            capturedRequest = request
            capturedCategory = category
            FakeHttpResponse(statusCode = 200, data = "")
        }
        val transport = StreamingTransportImpl(client)
        val uri = URI("https://streaming.split.io/sse?token=xyz")

        val connection = transport.connect(uri)
        connection.execute()

        assertNotNull(capturedRequest)
        assertEquals(uri, capturedRequest?.uri)
        assertEquals(HttpMethod.GET, capturedRequest?.method)
        assertNull(capturedRequest?.body)
        assertEquals("text/event-stream", capturedRequest?.headers?.get("Accept"))
        assertEquals(RequestCategory.SSE, capturedCategory)
    }

    @Test
    fun `execute returns StreamingResponse with success status`() = runBlocking {
        val client = FakeRetryableHttpClient { _, _ ->
            FakeHttpResponse(statusCode = 200, data = "data: test")
        }
        val transport = StreamingTransportImpl(client)
        val uri = URI("https://streaming.split.io/sse")

        val connection = transport.connect(uri)
        val response = connection.execute()

        assertTrue(response.isSuccess())
        assertEquals(200, response.getHttpStatus())
    }

    @Test
    fun `execute returns StreamingResponse with error status`() = runBlocking {
        val client = FakeRetryableHttpClient { _, _ ->
            FakeHttpResponse(statusCode = 500, data = null)
        }
        val transport = StreamingTransportImpl(client)
        val uri = URI("https://streaming.split.io/sse")

        val connection = transport.connect(uri)
        val response = connection.execute()

        assertFalse(response.isSuccess())
        assertEquals(500, response.getHttpStatus())
    }

    @Test
    fun `StreamingResponse isClientRelatedError delegates to HttpResponse`() = runBlocking {
        val client = FakeRetryableHttpClient { _, _ ->
            FakeHttpResponse(statusCode = 404, data = null, isClientError = true)
        }
        val transport = StreamingTransportImpl(client)
        val connection = transport.connect(URI("https://test.io/sse"))

        val response = connection.execute()

        assertTrue(response.isClientRelatedError())
    }

    @Test
    fun `StreamingResponse getBufferedReader returns reader from data`() = runBlocking {
        val client = FakeRetryableHttpClient { _, _ ->
            FakeHttpResponse(statusCode = 200, data = "line1\nline2\nline3")
        }
        val transport = StreamingTransportImpl(client)
        val connection = transport.connect(URI("https://test.io/sse"))

        val response = connection.execute()
        val reader = response.getBufferedReader()

        assertNotNull(reader)
        assertEquals("line1", reader?.readLine())
        assertEquals("line2", reader?.readLine())
        assertEquals("line3", reader?.readLine())
        assertNull(reader?.readLine())
    }

    @Test
    fun `StreamingResponse getBufferedReader returns null when data is null`() = runBlocking {
        val client = FakeRetryableHttpClient { _, _ ->
            FakeHttpResponse(statusCode = 200, data = null)
        }
        val transport = StreamingTransportImpl(client)
        val connection = transport.connect(URI("https://test.io/sse"))

        val response = connection.execute()
        val reader = response.getBufferedReader()

        assertNull(reader)
    }

    @Test
    fun `connection close is callable`() {
        val client = FakeRetryableHttpClient()
        val transport = StreamingTransportImpl(client)
        val connection = transport.connect(URI("https://test.io/sse"))

        // Should not throw
        connection.close()
    }

    @Test
    fun `response close is callable`() = runBlocking {
        val client = FakeRetryableHttpClient { _, _ ->
            FakeHttpResponse(statusCode = 200, data = "test")
        }
        val transport = StreamingTransportImpl(client)
        val connection = transport.connect(URI("https://test.io/sse"))
        val response = connection.execute()

        // Should not throw
        response.close()
    }
}
