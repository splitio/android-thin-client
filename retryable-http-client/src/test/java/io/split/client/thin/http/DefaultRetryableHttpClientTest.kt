package io.split.client.thin.http

import io.split.android.client.backoff.BackoffCounter
import io.split.client.thin.http.contracts.HttpException
import io.split.client.thin.http.contracts.HttpMethod
import io.split.client.thin.http.contracts.HttpResponse
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

    private val successResponse = FakeHttpResponse(isSuccess = true, httpStatus = 200)
    private val failureResponse = FakeHttpResponse(isSuccess = false, httpStatus = 500)
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

    private lateinit var httpClient: FakeHttpClient
    private lateinit var responseSequence: MutableList<() -> HttpResponse>
    private lateinit var client: DefaultRetryableHttpClient

    @Before
    fun setUp() {
        `when`(backoffCounter.nextRetryTime).thenReturn(1L)

        responseSequence = mutableListOf()
        httpClient = FakeHttpClient { _, _, _, _ ->
            FakeHttpRequest {
                if (responseSequence.isEmpty()) {
                    throw RuntimeException("No more responses in sequence")
                }
                responseSequence.removeAt(0)()
            }
        }

        client = DefaultRetryableHttpClient(
            httpClient = httpClient,
            policiesByCategory = categoryPoliciesMap,
            backoffFactory = { _ -> backoffCounter },
        )
    }

    @Test
    fun `returns response immediately on first-attempt success`() = runTest {
        responseSequence.add { successResponse }

        val result = client.execute(descriptor, RequestCategory.EVALUATIONS)

        assertSame(successResponse, result)
        assertEquals(1, httpClient.getTotalExecuteCalls())
    }

    @Test
    fun `retries on failure and returns response on subsequent success`() = runTest {
        responseSequence.add { failureResponse }
        responseSequence.add { successResponse }

        val result = client.execute(descriptor, RequestCategory.EVALUATIONS)

        assertSame(successResponse, result)
        assertEquals(2, httpClient.getTotalExecuteCalls())
    }

    @Test
    fun `returns last response when maxAttempts exhausted`() = runTest {
        responseSequence.add { failureResponse }
        responseSequence.add { failureResponse }
        responseSequence.add { failureResponse }

        val result = client.execute(descriptor, RequestCategory.EVALUATIONS)

        assertSame(failureResponse, result)
        assertEquals(3, httpClient.getTotalExecuteCalls())
    }

    @Test
    fun `does not retry when status-specific policy is null`() = runTest {
        val noRetry404 = CategoryRetryPolicies(
            default = defaultPolicy,
            byStatus = mapOf(404 to null),
        )
        categoryPoliciesMap[RequestCategory.EVALUATIONS] = noRetry404

        val response404 = FakeHttpResponse(isSuccess = false, httpStatus = 404)
        responseSequence.add { response404 }

        val result = client.execute(descriptor, RequestCategory.EVALUATIONS)

        assertSame(response404, result)
        assertEquals(1, httpClient.getTotalExecuteCalls())
    }

    @Test
    fun `calls backoff counter getNextRetryTime between retries`() = runTest {
        responseSequence.add { failureResponse }
        responseSequence.add { successResponse }

        client.execute(descriptor, RequestCategory.EVALUATIONS)

        verify(backoffCounter, times(1)).nextRetryTime
    }

    @Test(expected = HttpException::class)
    fun `rethrows HttpException with SSL status code without retrying`() = runTest {
        responseSequence.add { throw HttpException("SSL error", 9009) }

        client.execute(descriptor, RequestCategory.EVALUATIONS)
    }

    @Test
    fun `does not retry on SSL HttpException`() = runTest {
        responseSequence.add { throw HttpException("SSL error", 9009) }

        try {
            client.execute(descriptor, RequestCategory.EVALUATIONS)
            fail("Expected HttpException")
        } catch (_: HttpException) {
            // expected
        }

        assertEquals(1, httpClient.getTotalExecuteCalls())
    }

    @Test
    fun `retries on non-SSL HttpException and succeeds`() = runTest {
        responseSequence.add { throw HttpException("server error", 500) }
        responseSequence.add { successResponse }

        val result = client.execute(descriptor, RequestCategory.EVALUATIONS)

        assertSame(successResponse, result)
        assertEquals(2, httpClient.getTotalExecuteCalls())
    }

    @Test(expected = HttpException::class)
    fun `rethrows HttpException when retries exhausted`() = runTest {
        responseSequence.add { throw HttpException("server error", 500) }
        responseSequence.add { throw HttpException("server error", 500) }
        responseSequence.add { throw HttpException("server error", 500) }

        client.execute(descriptor, RequestCategory.EVALUATIONS)
    }

    @Test
    fun `executes once and returns response for unconfigured category`() = runTest {
        responseSequence.add { failureResponse }

        val result = client.execute(descriptor, RequestCategory.AUTH)

        assertSame(failureResponse, result)
        assertEquals(1, httpClient.getTotalExecuteCalls())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `respects coroutine cancellation`() = runTest {
        responseSequence.add { failureResponse }
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
        responseSequence.add { throw HttpException("network error") }
        responseSequence.add { successResponse }

        val result = client.execute(descriptor, RequestCategory.EVALUATIONS)

        assertSame(successResponse, result)
        assertEquals(2, httpClient.getTotalExecuteCalls())
    }

    @Test(expected = HttpException::class)
    fun `rethrows HttpException without status code when retries exhausted`() = runTest {
        responseSequence.add { throw HttpException("network error") }
        responseSequence.add { throw HttpException("network error") }
        responseSequence.add { throw HttpException("network error") }

        client.execute(descriptor, RequestCategory.EVALUATIONS)
    }

    @Test
    fun `does not retry when HttpException status is explicitly mapped to null in byStatus`() = runTest {
        val noRetry404 = CategoryRetryPolicies(
            default = defaultPolicy,
            byStatus = mapOf(404 to null),
        )
        categoryPoliciesMap[RequestCategory.EVALUATIONS] = noRetry404

        responseSequence.add { throw HttpException("not found", 404) }

        try {
            client.execute(descriptor, RequestCategory.EVALUATIONS)
            fail("Expected HttpException")
        } catch (_: HttpException) {
            // expected
        }

        assertEquals(1, httpClient.getTotalExecuteCalls())
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

        val response429 = FakeHttpResponse(isSuccess = false, httpStatus = 429)
        responseSequence.add { response429 }
        responseSequence.add { successResponse }

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
        responseSequence.add { successResponse }

        val result = client.execute(descriptorWithBody, RequestCategory.EVALUATIONS)

        assertSame(successResponse, result)
    }

    @Test
    fun `retries on HttpException when status has a specific non-null policy override`() = runTest {
        val statusSpecificPolicy = RetryPolicy(maxAttempts = 2, backoffBaseSeconds = 1)
        val policiesWithStatusOverride = CategoryRetryPolicies(
            default = defaultPolicy,
            byStatus = mapOf(429 to statusSpecificPolicy),
        )
        categoryPoliciesMap[RequestCategory.EVALUATIONS] = policiesWithStatusOverride

        responseSequence.add { throw HttpException("rate limited", 429) }
        responseSequence.add { successResponse }

        val result = client.execute(descriptor, RequestCategory.EVALUATIONS)

        assertSame(successResponse, result)
        assertEquals(2, httpClient.getTotalExecuteCalls())
    }

    @Test(expected = HttpException::class)
    fun `rethrows HttpException when status-specific policy retries exhausted`() = runTest {
        val statusSpecificPolicy = RetryPolicy(maxAttempts = 2, backoffBaseSeconds = 1)
        val policiesWithStatusOverride = CategoryRetryPolicies(
            default = defaultPolicy,
            byStatus = mapOf(429 to statusSpecificPolicy),
        )
        categoryPoliciesMap[RequestCategory.EVALUATIONS] = policiesWithStatusOverride

        responseSequence.add { throw HttpException("rate limited", 429) }
        responseSequence.add { throw HttpException("rate limited", 429) }

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
        responseSequence.add { successResponse }

        val result = client.execute(descriptorWithHeaders, RequestCategory.EVALUATIONS)

        assertSame(successResponse, result)
    }

    @Test
    fun `propagates non-HttpException immediately without retrying`() = runTest {
        val cause = RuntimeException("unexpected failure")
        responseSequence.add { throw cause }

        try {
            client.execute(descriptor, RequestCategory.EVALUATIONS)
            fail("Expected RuntimeException")
        } catch (e: RuntimeException) {
            assertSame(cause, e)
        }

        assertEquals(1, httpClient.getTotalExecuteCalls())
    }

    @Test
    fun `304 not modified is treated as success, not retried, and fires onHttpRequestSucceeded`() = runTest {
        val response304 = FakeHttpResponse(isSuccess = false, httpStatus = 304)
        responseSequence.add { response304 }

        val successCallbacks = mutableListOf<HttpResponse>()
        val trackingClient = DefaultRetryableHttpClient(
            httpClient = httpClient,
            policiesByCategory = categoryPoliciesMap,
            backoffFactory = { _ -> backoffCounter },
            onHttpRequestSucceeded = { response, _ -> successCallbacks.add(response) },
        )

        val result = trackingClient.execute(descriptor, RequestCategory.EVALUATIONS)

        assertSame(response304, result)
        assertEquals(1, httpClient.getTotalExecuteCalls())
        assertEquals(1, successCallbacks.size)
        assertSame(response304, successCallbacks[0])
    }

    @Test
    fun `does not start next attempt when coroutine is cancelled after execute returns`() = runTest {
        responseSequence.add { failureResponse }
        `when`(backoffCounter.nextRetryTime).thenReturn(60L)

        val job = launch {
            client.execute(descriptor, RequestCategory.EVALUATIONS)
        }

        advanceTimeBy(1) // first attempt runs; coroutine now suspended in 60s delay
        job.cancel()
        job.join()

        assert(job.isCancelled)
        assertEquals(1, httpClient.getTotalExecuteCalls())
    }
}
