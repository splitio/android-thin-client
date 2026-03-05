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

    // --- Scenario 1: First-attempt success ---

    @Test
    fun `returns response immediately on first-attempt success`() = runTest {
        `when`(httpRequest.execute()).thenReturn(successResponse)

        val result = client.execute(descriptor, RequestCategory.EVALUATIONS)

        assertSame(successResponse, result)
        verify(httpRequest, times(1)).execute()
    }

    // --- Scenario 2: Retries on failure, succeeds on second attempt ---

    @Test
    fun `retries on failure and returns response on subsequent success`() = runTest {
        `when`(httpRequest.execute())
            .thenReturn(failureResponse)
            .thenReturn(successResponse)

        val result = client.execute(descriptor, RequestCategory.EVALUATIONS)

        assertSame(successResponse, result)
        verify(httpRequest, times(2)).execute()
    }

    // --- Scenario 3: Exhausts retries and returns last response ---

    @Test
    fun `returns last response when maxAttempts exhausted`() = runTest {
        `when`(httpRequest.execute()).thenReturn(failureResponse)

        val result = client.execute(descriptor, RequestCategory.EVALUATIONS)

        assertSame(failureResponse, result)
        verify(httpRequest, times(3)).execute()
    }

    // --- Scenario 4: Status-specific null policy means no retry ---

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

    // --- Scenario 5: Calls BackoffCounter between retries ---

    @Test
    fun `calls backoff counter getNextRetryTime between retries`() = runTest {
        `when`(httpRequest.execute())
            .thenReturn(failureResponse)
            .thenReturn(successResponse)

        client.execute(descriptor, RequestCategory.EVALUATIONS)

        verify(backoffCounter, times(1)).nextRetryTime
    }

    // --- Scenario 6: Rethrows HttpException with status 9009 (SSL) ---

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

    // --- Scenario 7: Retries on non-SSL HttpException, rethrows when exhausted ---

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

    // --- Scenario 8: Throws IllegalStateException for unconfigured category ---

    @Test(expected = IllegalStateException::class)
    fun `throws IllegalStateException when category has no configured policies`() = runTest {
        client.execute(descriptor, RequestCategory.AUTH)
    }

    // --- Scenario 9: Respects coroutine cancellation ---

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

    // --- Scenario 10: Network error without status code uses default policy ---

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

    // --- Scenario 11: HttpException with status mapped to null in byStatus does not retry ---

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

    // --- Scenario 12: Status-specific policy backoff base is used when retrying ---

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

    // --- Scenario 13: Request with body and headers ---

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

    // --- Scenario 14: HttpException with status mapped to a specific non-null policy ---

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

    // --- Scenario 15: Request with headers but no body ---

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

    // --- Scenario 16: Non-HttpException propagates without retry ---

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

    // --- Scenario 17: Cancellation stops further attempts ---

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
