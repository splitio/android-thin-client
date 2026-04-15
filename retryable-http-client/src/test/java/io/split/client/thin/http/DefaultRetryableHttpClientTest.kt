package io.split.client.thin.http

import io.split.android.client.backoff.BackoffCounter
import io.split.android.client.network.HttpClient
import io.split.android.client.network.HttpException
import io.split.android.client.network.HttpMethod
import io.split.android.client.network.HttpRequest
import io.split.android.client.network.HttpResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.net.URI

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultRetryableHttpClientTest {

    private val httpClient = mock(HttpClient::class.java)
    private val httpRequest = mock(HttpRequest::class.java)
    private val successResponse = mock(HttpResponse::class.java)
    private val failureResponse = mock(HttpResponse::class.java)
    private val backoffCounter = mock(BackoffCounter::class.java)

    private val descriptor = HttpRequestDescriptor(
        uri = URI.create("https://api.split.io/test"),
        method = HttpMethod.GET,
    )

    private val defaultPolicy = RetryPolicy(maxAttempts = 3, backoffBaseSeconds = 1)
    private val defaultPolicies = CategoryRetryPolicies(default = defaultPolicy)
    private val categoryPoliciesMap = mutableMapOf(
        RequestCategory.EVALUATIONS to defaultPolicies,
    )

    private lateinit var client: DefaultRetryableHttpClient

    @Before
    fun setUp() {
        `when`(successResponse.isSuccess).thenReturn(true)
        `when`(successResponse.httpStatus).thenReturn(200)
        `when`(failureResponse.isSuccess).thenReturn(false)
        `when`(failureResponse.httpStatus).thenReturn(500)
        `when`(backoffCounter.nextRetryTime).thenReturn(1L)
        `when`(httpClient.request(descriptor.uri, descriptor.method)).thenReturn(httpRequest)

        client = DefaultRetryableHttpClient(
            httpClient = httpClient,
            policiesByCategory = categoryPoliciesMap,
            backoffFactory = { _ -> backoffCounter },
        )
    }

    @Test
    fun `returns response immediately on first-attempt success`() = runTest {
        `when`(httpRequest.execute()).thenReturn(successResponse)

        val result = client.execute(descriptor, RequestCategory.EVALUATIONS)

        assertSame(successResponse, result)
        verify(httpRequest, times(1)).execute()
    }

    @Test
    fun `retries on failure and returns response on subsequent success`() = runTest {
        `when`(httpRequest.execute())
            .thenReturn(failureResponse)
            .thenReturn(successResponse)

        val result = client.execute(descriptor, RequestCategory.EVALUATIONS)

        assertSame(successResponse, result)
        verify(httpRequest, times(2)).execute()
    }

    @Test
    fun `returns last response when maxAttempts exhausted`() = runTest {
        `when`(httpRequest.execute()).thenReturn(failureResponse)

        val result = client.execute(descriptor, RequestCategory.EVALUATIONS)

        assertSame(failureResponse, result)
        verify(httpRequest, times(3)).execute()
    }

    @Test
    fun `does not retry when status-specific policy is null`() = runTest {
        val noRetry404 = CategoryRetryPolicies(
            default = defaultPolicy,
            byStatus = mapOf(404 to null),
        )
        categoryPoliciesMap[RequestCategory.EVALUATIONS] = noRetry404

        val response404 = mock(HttpResponse::class.java)
        `when`(response404.isSuccess).thenReturn(false)
        `when`(response404.httpStatus).thenReturn(404)
        `when`(httpRequest.execute()).thenReturn(response404)

        val result = client.execute(descriptor, RequestCategory.EVALUATIONS)

        assertSame(response404, result)
        verify(httpRequest, times(1)).execute()
    }

    @Test
    fun `calls backoff counter getNextRetryTime between retries`() = runTest {
        `when`(httpRequest.execute())
            .thenReturn(failureResponse)
            .thenReturn(successResponse)

        client.execute(descriptor, RequestCategory.EVALUATIONS)

        verify(backoffCounter, times(1)).nextRetryTime
    }

    @Test(expected = HttpException::class)
    fun `rethrows HttpException with SSL status code without retrying`() = runTest {
        `when`(httpRequest.execute()).thenThrow(HttpException("SSL error", 9009))

        client.execute(descriptor, RequestCategory.EVALUATIONS)
    }

    @Test
    fun `does not retry on SSL HttpException`() = runTest {
        `when`(httpRequest.execute()).thenThrow(HttpException("SSL error", 9009))

        try {
            client.execute(descriptor, RequestCategory.EVALUATIONS)
            fail("Expected HttpException")
        } catch (_: HttpException) {
            // expected
        }

        verify(httpRequest, times(1)).execute()
    }

    @Test
    fun `retries on non-SSL HttpException and succeeds`() = runTest {
        `when`(httpRequest.execute())
            .thenThrow(HttpException("server error", 500))
            .thenReturn(successResponse)

        val result = client.execute(descriptor, RequestCategory.EVALUATIONS)

        assertSame(successResponse, result)
        verify(httpRequest, times(2)).execute()
    }

    @Test(expected = HttpException::class)
    fun `rethrows HttpException when retries exhausted`() = runTest {
        `when`(httpRequest.execute()).thenThrow(HttpException("server error", 500))

        client.execute(descriptor, RequestCategory.EVALUATIONS)
    }

    @Test
    fun `executes once and returns response for unconfigured category`() = runTest {
        `when`(httpRequest.execute()).thenReturn(failureResponse)

        val result = client.execute(descriptor, RequestCategory.AUTH)

        assertSame(failureResponse, result)
        verify(httpRequest, times(1)).execute()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `respects coroutine cancellation`() = runTest {
        `when`(httpRequest.execute()).thenReturn(failureResponse)
        `when`(backoffCounter.nextRetryTime).thenReturn(60L)

        val job = launch {
            client.execute(descriptor, RequestCategory.EVALUATIONS)
        }

        advanceTimeBy(500)
        job.cancel()

        try {
            job.join()
        } catch (_: CancellationException) {
            // expected
        }

        assert(job.isCancelled)
    }

    @Test
    fun `retries on HttpException without status code using default policy`() = runTest {
        `when`(httpRequest.execute())
            .thenThrow(HttpException("network error"))
            .thenReturn(successResponse)

        val result = client.execute(descriptor, RequestCategory.EVALUATIONS)

        assertSame(successResponse, result)
        verify(httpRequest, times(2)).execute()
    }

    @Test(expected = HttpException::class)
    fun `rethrows HttpException without status code when retries exhausted`() = runTest {
        `when`(httpRequest.execute()).thenThrow(HttpException("network error"))

        client.execute(descriptor, RequestCategory.EVALUATIONS)
    }

    @Test
    fun `does not retry when HttpException status is explicitly mapped to null in byStatus`() = runTest {
        val noRetry404 = CategoryRetryPolicies(
            default = defaultPolicy,
            byStatus = mapOf(404 to null),
        )
        categoryPoliciesMap[RequestCategory.EVALUATIONS] = noRetry404

        `when`(httpRequest.execute()).thenThrow(HttpException("not found", 404))

        try {
            client.execute(descriptor, RequestCategory.EVALUATIONS)
            fail("Expected HttpException")
        } catch (_: HttpException) {
            // expected
        }

        verify(httpRequest, times(1)).execute()
    }

    @Test
    fun `uses status-specific backoff base when a status-specific policy is in effect`() = runTest {
        val statusSpecificBackoffBase = 5
        val backoffCounterForStatusPolicy = mock(BackoffCounter::class.java)
        `when`(backoffCounterForStatusPolicy.nextRetryTime).thenReturn(1L)

        val statusSpecificPolicy = RetryPolicy(maxAttempts = 3, backoffBaseSeconds = statusSpecificBackoffBase)
        val policiesWithStatusOverride = CategoryRetryPolicies(
            default = defaultPolicy,
            byStatus = mapOf(429 to statusSpecificPolicy),
        )
        categoryPoliciesMap[RequestCategory.EVALUATIONS] = policiesWithStatusOverride

        val capturedBases = mutableListOf<Int>()
        val trackingClient = DefaultRetryableHttpClient(
            httpClient = httpClient,
            policiesByCategory = categoryPoliciesMap,
            backoffFactory = { base ->
                capturedBases.add(base)
                if (base == statusSpecificBackoffBase) backoffCounterForStatusPolicy else backoffCounter
            },
        )

        val response429 = mock(HttpResponse::class.java)
        `when`(response429.isSuccess).thenReturn(false)
        `when`(response429.httpStatus).thenReturn(429)
        `when`(httpRequest.execute())
            .thenReturn(response429)
            .thenReturn(successResponse)

        trackingClient.execute(descriptor, RequestCategory.EVALUATIONS)

        assert(capturedBases.contains(statusSpecificBackoffBase)) {
            "Expected backoffFactory to be called with status-specific base $statusSpecificBackoffBase, got: $capturedBases"
        }
    }

    @Test
    fun `uses body and headers from descriptor`() = runTest {
        val descriptorWithBody = HttpRequestDescriptor(
            uri = URI.create("https://api.split.io/test"),
            method = HttpMethod.POST,
            body = """{"event":"test"}""",
            headers = mapOf("X-Custom" to "value"),
        )
        `when`(httpClient.request(descriptorWithBody.uri, descriptorWithBody.method, descriptorWithBody.body, descriptorWithBody.headers))
            .thenReturn(httpRequest)
        `when`(httpRequest.execute()).thenReturn(successResponse)

        val result = client.execute(descriptorWithBody, RequestCategory.EVALUATIONS)

        assertSame(successResponse, result)
        verify(httpClient).request(
            descriptorWithBody.uri,
            descriptorWithBody.method,
            descriptorWithBody.body,
            descriptorWithBody.headers,
        )
    }

    @Test
    fun `retries on HttpException when status has a specific non-null policy override`() = runTest {
        val statusSpecificPolicy = RetryPolicy(maxAttempts = 2, backoffBaseSeconds = 1)
        val policiesWithStatusOverride = CategoryRetryPolicies(
            default = defaultPolicy,
            byStatus = mapOf(429 to statusSpecificPolicy),
        )
        categoryPoliciesMap[RequestCategory.EVALUATIONS] = policiesWithStatusOverride

        `when`(httpRequest.execute())
            .thenThrow(HttpException("rate limited", 429))
            .thenReturn(successResponse)

        val result = client.execute(descriptor, RequestCategory.EVALUATIONS)

        assertSame(successResponse, result)
        verify(httpRequest, times(2)).execute()
    }

    @Test(expected = HttpException::class)
    fun `rethrows HttpException when status-specific policy retries exhausted`() = runTest {
        val statusSpecificPolicy = RetryPolicy(maxAttempts = 2, backoffBaseSeconds = 1)
        val policiesWithStatusOverride = CategoryRetryPolicies(
            default = defaultPolicy,
            byStatus = mapOf(429 to statusSpecificPolicy),
        )
        categoryPoliciesMap[RequestCategory.EVALUATIONS] = policiesWithStatusOverride

        `when`(httpRequest.execute()).thenThrow(HttpException("rate limited", 429))

        client.execute(descriptor, RequestCategory.EVALUATIONS)
    }

    @Test
    fun `uses four-arg request overload when headers are present but body is null`() = runTest {
        val descriptorWithHeaders = HttpRequestDescriptor(
            uri = URI.create("https://api.split.io/test"),
            method = HttpMethod.GET,
            body = null,
            headers = mapOf("Authorization" to "Bearer token"),
        )
        `when`(httpClient.request(descriptorWithHeaders.uri, descriptorWithHeaders.method, null, descriptorWithHeaders.headers))
            .thenReturn(httpRequest)
        `when`(httpRequest.execute()).thenReturn(successResponse)

        val result = client.execute(descriptorWithHeaders, RequestCategory.EVALUATIONS)

        assertSame(successResponse, result)
        verify(httpClient).request(
            descriptorWithHeaders.uri,
            descriptorWithHeaders.method,
            null,
            descriptorWithHeaders.headers,
        )
    }

    @Test
    fun `propagates non-HttpException immediately without retrying`() = runTest {
        val cause = RuntimeException("unexpected failure")
        `when`(httpRequest.execute()).thenThrow(cause)

        try {
            client.execute(descriptor, RequestCategory.EVALUATIONS)
            fail("Expected RuntimeException")
        } catch (e: RuntimeException) {
            assertSame(cause, e)
        }

        verify(httpRequest, times(1)).execute()
    }

    @Test
    fun `304 not modified is treated as success, not retried, and fires onHttpRequestSucceeded`() = runTest {
        val response304 = mock(HttpResponse::class.java)
        `when`(response304.isSuccess).thenReturn(false)
        `when`(response304.httpStatus).thenReturn(304)
        `when`(httpRequest.execute()).thenReturn(response304)

        val successCallbacks = mutableListOf<HttpResponse>()
        val trackingClient = DefaultRetryableHttpClient(
            httpClient = httpClient,
            policiesByCategory = categoryPoliciesMap,
            backoffFactory = { _ -> backoffCounter },
            onHttpRequestSucceeded = { response, _ -> successCallbacks.add(response) },
        )

        val result = trackingClient.execute(descriptor, RequestCategory.EVALUATIONS)

        assertSame(response304, result)
        verify(httpRequest, times(1)).execute()
        assertEquals(1, successCallbacks.size)
        assertSame(response304, successCallbacks[0])
    }

    @Test
    fun `does not start next attempt when coroutine is cancelled after execute returns`() = runTest {
        `when`(httpRequest.execute()).thenReturn(failureResponse)
        `when`(backoffCounter.nextRetryTime).thenReturn(60L)

        val job = launch {
            client.execute(descriptor, RequestCategory.EVALUATIONS)
        }

        advanceTimeBy(1) // first attempt runs; coroutine now suspended in 60s delay
        job.cancel()
        job.join()

        assert(job.isCancelled)
        verify(httpRequest, times(1)).execute()
    }
}
