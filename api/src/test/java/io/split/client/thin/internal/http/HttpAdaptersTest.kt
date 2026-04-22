package io.split.client.thin.internal.http

import io.split.android.client.network.HttpException as AndroidHttpException
import io.split.android.client.network.HttpMethod as AndroidHttpMethod
import io.split.android.client.network.HttpRequest as AndroidHttpRequest
import io.split.android.client.network.HttpResponse as AndroidHttpResponse
import io.split.android.client.network.HttpClient as AndroidHttpClient
import io.split.client.thin.http.contracts.HttpException
import io.split.client.thin.http.contracts.HttpMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.net.URI

class HttpResponseAdapterTest {

    @Test
    fun `isSuccess delegates to delegate`() {
        val delegate = mock(AndroidHttpResponse::class.java)
        `when`(delegate.isSuccess).thenReturn(true)
        assertTrue(HttpResponseAdapter(delegate).isSuccess)
    }

    @Test
    fun `isSuccess returns false when delegate is false`() {
        val delegate = mock(AndroidHttpResponse::class.java)
        `when`(delegate.isSuccess).thenReturn(false)
        assertFalse(HttpResponseAdapter(delegate).isSuccess)
    }

    @Test
    fun `httpStatus delegates to delegate`() {
        val delegate = mock(AndroidHttpResponse::class.java)
        `when`(delegate.httpStatus).thenReturn(200)
        assertEquals(200, HttpResponseAdapter(delegate).httpStatus)
    }

    @Test
    fun `getData delegates data field to delegate`() {
        val delegate = mock(AndroidHttpResponse::class.java)
        `when`(delegate.data).thenReturn("body")
        assertEquals("body", HttpResponseAdapter(delegate).getData())
    }

    @Test
    fun `getData returns null when delegate data is null`() {
        val delegate = mock(AndroidHttpResponse::class.java)
        `when`(delegate.data).thenReturn(null)
        assertNull(HttpResponseAdapter(delegate).getData())
    }
}

class HttpRequestAdapterTest {

    @Test
    fun `execute returns response adapter wrapping delegate response`() {
        val delegate = mock(AndroidHttpRequest::class.java)
        val androidResponse = mock(AndroidHttpResponse::class.java)
        `when`(androidResponse.isSuccess).thenReturn(true)
        `when`(androidResponse.httpStatus).thenReturn(200)
        `when`(delegate.execute()).thenReturn(androidResponse)

        val result = HttpRequestAdapter(delegate).execute()

        assertTrue(result.isSuccess)
        assertEquals(200, result.httpStatus)
    }

    @Test(expected = HttpException::class)
    fun `execute wraps AndroidHttpException in contract HttpException`() {
        val delegate = mock(AndroidHttpRequest::class.java)
        `when`(delegate.execute()).thenThrow(AndroidHttpException("error", 500))

        HttpRequestAdapter(delegate).execute()
    }

    @Test
    fun `execute preserves status code from AndroidHttpException`() {
        val delegate = mock(AndroidHttpRequest::class.java)
        `when`(delegate.execute()).thenThrow(AndroidHttpException("error", 404))

        try {
            HttpRequestAdapter(delegate).execute()
        } catch (e: HttpException) {
            assertEquals(404, e.statusCode)
        }
    }
}

class HttpClientAdapterTest {

    private val androidClient = mock(AndroidHttpClient::class.java)
    private val adapter = HttpClientAdapter(androidClient)
    private val uri = URI.create("https://api.split.io/test")

    @Test
    fun `request with GET delegates to android client with GET method`() {
        val fakeRequest = mock(AndroidHttpRequest::class.java)
        `when`(androidClient.request(uri, AndroidHttpMethod.GET)).thenReturn(fakeRequest)

        adapter.request(uri, HttpMethod.GET)

        verify(androidClient).request(uri, AndroidHttpMethod.GET)
    }

    @Test
    fun `request with POST delegates to android client with POST method`() {
        val fakeRequest = mock(AndroidHttpRequest::class.java)
        `when`(androidClient.request(uri, AndroidHttpMethod.POST)).thenReturn(fakeRequest)

        adapter.request(uri, HttpMethod.POST)

        verify(androidClient).request(uri, AndroidHttpMethod.POST)
    }

    @Test
    fun `request with body and headers delegates correctly`() {
        val fakeRequest = mock(AndroidHttpRequest::class.java)
        val body = """{"key":"value"}"""
        val headers = mapOf("Authorization" to "Bearer token")
        `when`(androidClient.request(uri, AndroidHttpMethod.POST, body, headers)).thenReturn(fakeRequest)

        adapter.request(uri, HttpMethod.POST, body, headers)

        verify(androidClient).request(uri, AndroidHttpMethod.POST, body, headers)
    }

    @Test
    fun `request returns HttpRequestAdapter`() {
        val fakeRequest = mock(AndroidHttpRequest::class.java)
        `when`(androidClient.request(uri, AndroidHttpMethod.GET)).thenReturn(fakeRequest)

        val result = adapter.request(uri, HttpMethod.GET)

        assertTrue(result is HttpRequestAdapter)
    }
}
