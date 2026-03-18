package io.split.client.thin.internal.secure

import io.split.client.thin.http.RequestCategory
import io.split.client.thin.internal.auth.JwtCredential
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSecureHttpClientFetchEvaluationsTest {

    @Test
    fun `injects Authorization Bearer header`() = runTest {
        val jwt = JwtCredential("my-token", 9999999L, false)
        val auth = FakeAuthProvider(credential = jwt)
        val http = FakeRetryableHttpClient(statusCode = 200)
        val (client, _, _) = makeClient(auth, http)

        client.fetchEvaluations(testDefaultTarget, testDefaultFilters)

        assertEquals("Bearer my-token", http.lastRequest?.headers?.get("Authorization"))
    }

    @Test
    fun `sends POST to evaluationsUrl`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultFilters)

        assertTrue(http.lastRequest?.uri?.toString()?.startsWith(testEvaluationsUrl) == true)
    }

    @Test
    fun `uses EVALUATIONS category`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultFilters)

        assertEquals(RequestCategory.EVALUATIONS, http.lastCategory)
    }

    @Test
    fun `matchingKey sent as user query param`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultFilters)

        assertTrue(http.lastRequest?.uri?.query?.contains("user=user-1") == true)
    }

    @Test
    fun `matchingKey is NOT in request body`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultFilters)

        assertFalse(http.lastRequest?.body?.contains("matchingKey") == true)
        assertFalse(http.lastRequest?.body?.contains("user-1") == true)
    }

    @Test
    fun `attributes sent in body under attributes key`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultFilters)

        assertTrue(http.lastRequest?.body?.contains("\"attributes\"") == true)
        assertTrue(http.lastRequest?.body?.contains("premium") == true)
    }

    @Test
    fun `flagNames sent as flags query params`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultFilters)

        val query = http.lastRequest?.uri?.query ?: ""
        assertTrue(query.contains("flags=flag-a"))
        assertTrue(query.contains("flags=flag-b"))
    }

    @Test
    fun `flagNames are NOT in request body`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultFilters)

        assertFalse(http.lastRequest?.body?.contains("flag-a") == true)
        assertFalse(http.lastRequest?.body?.contains("flag-b") == true)
    }

    @Test
    fun `flagSets sent as sets query params`() = runTest {
        val target = testDefaultTarget
        val filters = EvaluationFilters(flagNames = null, flagSets = setOf("set-x", "set-y"))
        val (client, _, http) = makeClient()

        client.fetchEvaluations(target, filters)

        val query = http.lastRequest?.uri?.query ?: ""
        assertTrue(query.contains("sets=set-x"))
        assertTrue(query.contains("sets=set-y"))
    }

    @Test
    fun `changeNumber sent as query param`() = runTest {
        val filters = EvaluationFilters(flagNames = null, flagSets = null, changeNumber = 42L)
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, filters)

        assertTrue(http.lastRequest?.uri?.query?.contains("changeNumber=42") == true)
    }

    @Test
    fun `changeNumber defaults to -1 when null filters`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, null)

        assertTrue(http.lastRequest?.uri?.query?.contains("changeNumber=-1") == true)
    }

    @Test
    fun `with null filters body only contains empty object or attributes`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, null)

        assertTrue(http.lastRequest?.uri?.toString()?.startsWith(testEvaluationsUrl) == true)
        assertTrue(http.lastRequest?.body?.contains("user-1") == false)
    }

    @Test
    fun `target with no attributes sends empty body`() = runTest {
        val targetWithNoAttrs = EvaluationTarget(matchingKey = "user-2", bucketingKey = null, attributes = null)
        val (client, _, http) = makeClient()

        client.fetchEvaluations(targetWithNoAttrs, null)

        assertEquals("{}", http.lastRequest?.body)
    }

    @Test
    fun `withConfig sent as query param when set`() = runTest {
        val filters = EvaluationFilters(flagNames = null, flagSets = null, withConfig = true)
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, filters)

        assertTrue(http.lastRequest?.uri?.query?.contains("withConfig=true") == true)
    }

    @Test
    fun `withConfig not sent when null`() = runTest {
        val filters = EvaluationFilters(flagNames = null, flagSets = null, withConfig = null)
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, filters)

        assertFalse(http.lastRequest?.uri?.query?.contains("withConfig") == true)
    }

    @Test
    fun `impressionsMode sent as query param when configured`() = runTest {
        val (client, _, http) = makeClient(impressionsMode = 1)

        client.fetchEvaluations(testDefaultTarget, testDefaultFilters)

        assertTrue(http.lastRequest?.uri?.query?.contains("impressionsMode=1") == true)
    }

    @Test
    fun `impressionsMode not sent when not configured`() = runTest {
        val (client, _, http) = makeClient(impressionsMode = null)

        client.fetchEvaluations(testDefaultTarget, testDefaultFilters)

        assertFalse(http.lastRequest?.uri?.query?.contains("impressionsMode") == true)
    }

    @Test
    fun `on 401 invalidates and retries once`() = runTest {
        val firstToken = JwtCredential("first-token", 9999999L, false)
        val secondToken = JwtCredential("second-token", 9999999L, false)
        val auth = FakeAuthProvider(credentialSequence = listOf(firstToken, secondToken))
        val http = FakeRetryableHttpClient(statusCodeSequence = listOf(401, 200))
        val (client, _, _) = makeClient(auth, http)

        client.fetchEvaluations(testDefaultTarget, testDefaultFilters)

        assertEquals(1, auth.invalidateCallCount)
        assertEquals(2, http.executeCallCount)
        assertEquals("Bearer second-token", http.requests[1].headers["Authorization"])
    }

    @Test
    fun `401 retry only happens once even if second call also returns 401`() = runTest {
        val auth = FakeAuthProvider(credentialSequence = listOf(
            JwtCredential("t1", 9999999L, false),
            JwtCredential("t2", 9999999L, false),
        ))
        val http = FakeRetryableHttpClient(statusCodeSequence = listOf(401, 401))
        val (client, _, _) = makeClient(auth, http)

        val result = client.fetchEvaluations(testDefaultTarget, testDefaultFilters)

        assertEquals(2, http.executeCallCount)
        assertEquals(401, result.httpStatus)
    }

    @Test
    fun `non-401 error is returned without retry`() = runTest {
        val http = FakeRetryableHttpClient(statusCode = 500)
        val (client, _, _) = makeClient(httpClient = http)

        val result = client.fetchEvaluations(testDefaultTarget, testDefaultFilters)

        assertEquals(1, http.executeCallCount)
        assertEquals(500, result.httpStatus)
    }

    @Test
    fun `propagates AuthProvider exception`() = runTest {
        val auth = FakeAuthProvider(throwOnCredential = RuntimeException("auth failed"))
        val (client, _, _) = makeClient(auth)
        var thrown: Throwable? = null

        try {
            client.fetchEvaluations(testDefaultTarget, testDefaultFilters)
        } catch (e: RuntimeException) {
            thrown = e
        }

        assertTrue("Expected RuntimeException", thrown is RuntimeException)
        assertEquals("auth failed", thrown?.message)
    }

    @Test
    fun `propagates RetryableHttpClient exception`() = runTest {
        val http = FakeRetryableHttpClient(throwOnExecute = RuntimeException("network failed"))
        val (client, _, _) = makeClient(httpClient = http)
        var thrown: Throwable? = null

        try {
            client.fetchEvaluations(testDefaultTarget, testDefaultFilters)
        } catch (e: RuntimeException) {
            thrown = e
        }

        assertTrue("Expected RuntimeException", thrown is RuntimeException)
        assertEquals("network failed", thrown?.message)
    }
}
