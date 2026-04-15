package io.split.client.thin.internal.secure

import io.split.client.thin.http.RequestCategory
import io.split.client.thin.internal.auth.JwtCredential
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DefaultSecureHttpClientFetchEvaluationsTest {

    @Test
    fun `injects Authorization Bearer header`() = runTest {
        val jwt = JwtCredential("my-token", 9999999L, false)
        val auth = FakeAuthProvider(credential = jwt)
        val http = FakeRetryableHttpClient(statusCode = 200)
        val (client, _, _) = makeClient(auth, http)

        client.fetchEvaluations(testDefaultTarget, testDefaultFilters, -1L)

        assertEquals("Bearer my-token", http.lastRequest?.headers?.get("Authorization"))
    }

    @Test
    fun `sends POST to evaluationsUrl`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultFilters, -1L)

        assertTrue(http.lastRequest?.uri?.toString()?.startsWith(testEvaluationsUrl) == true)
    }

    @Test
    fun `uses EVALUATIONS category`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultFilters, -1L)

        assertEquals(RequestCategory.EVALUATIONS, http.lastCategory)
    }

    @Test
    fun `matchingKey sent as user query param`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultFilters, -1L)

        assertTrue(http.lastRequest?.uri?.query?.contains("user=user-1") == true)
    }

    @Test
    fun `matchingKey is NOT in request body`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultFilters, -1L)

        assertFalse(http.lastRequest?.body?.contains("matchingKey") == true)
        assertFalse(http.lastRequest?.body?.contains("user-1") == true)
    }

    @Test
    fun `attributes sent in body under attributes key`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultFilters, -1L)

        assertTrue(http.lastRequest?.body?.contains("\"attributes\"") == true)
        assertTrue(http.lastRequest?.body?.contains("premium") == true)
    }

    @Test
    fun `flagNames sent as flags query params`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultFilters, -1L)

        val query = http.lastRequest?.uri?.query ?: ""
        assertTrue(query.contains("flags=flag-a"))
        assertTrue(query.contains("flags=flag-b"))
    }

    @Test
    fun `flagNames are NOT in request body`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultFilters, -1L)

        assertFalse(http.lastRequest?.body?.contains("flag-a") == true)
        assertFalse(http.lastRequest?.body?.contains("flag-b") == true)
    }

    @Test
    fun `flagSets sent as sets query params`() = runTest {
        val target = testDefaultTarget
        val filters = EvaluationFilters(flagNames = null, flagSets = setOf("set-x", "set-y"))
        val (client, _, http) = makeClient()

        client.fetchEvaluations(target, filters, -1L)

        val query = http.lastRequest?.uri?.query ?: ""
        assertTrue(query.contains("sets=set-x"))
        assertTrue(query.contains("sets=set-y"))
    }

    @Test
    fun `changeNumber sent as since query param`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, null, 42L)

        assertTrue(http.lastRequest?.uri?.query?.contains("since=42") == true)
    }

    @Test
    fun `changeNumber -1 sent as since=-1`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, null, -1L)

        assertTrue(http.lastRequest?.uri?.query?.contains("since=-1") == true)
    }

    @Test
    fun `with null filters body only contains empty object or attributes`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, null, -1L)

        assertTrue(http.lastRequest?.uri?.toString()?.startsWith(testEvaluationsUrl) == true)
        assertTrue(http.lastRequest?.body?.contains("user-1") == false)
    }

    @Test
    fun `target with no attributes sends empty body`() = runTest {
        val targetWithNoAttrs = EvaluationTarget(matchingKey = "user-2", bucketingKey = null, attributes = null)
        val (client, _, http) = makeClient()

        client.fetchEvaluations(targetWithNoAttrs, null, -1L)

        assertEquals("{}", http.lastRequest?.body)
    }

    @Test
    fun `withDynamicConfig sent as query param when set`() = runTest {
        val filters = EvaluationFilters(flagNames = null, flagSets = null, withDynamicConfig = true)
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, filters, -1L)

        assertTrue(http.lastRequest?.uri?.query?.contains("withDynamicConfig=true") == true)
    }

    @Test
    fun `withDynamicConfig not sent when null`() = runTest {
        val filters = EvaluationFilters(flagNames = null, flagSets = null, withDynamicConfig = null)
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, filters, -1L)

        assertFalse(http.lastRequest?.uri?.query?.contains("withDynamicConfig") == true)
    }

    @Test
    fun `bucketingKey sent as query param when non-null`() = runTest {
        val target = EvaluationTarget(matchingKey = "user-1", bucketingKey = "bucket-key", attributes = null)
        val (client, _, http) = makeClient()

        client.fetchEvaluations(target, testDefaultFilters, -1L)

        assertTrue(http.lastRequest?.uri?.query?.contains("bucketingKey=bucket-key") == true)
    }

    @Test
    fun `bucketingKey not sent when null`() = runTest {
        val target = EvaluationTarget(matchingKey = "user-1", bucketingKey = null, attributes = null)
        val (client, _, http) = makeClient()

        client.fetchEvaluations(target, testDefaultFilters, -1L)

        assertFalse(http.lastRequest?.uri?.query?.contains("bucketingKey") == true)
    }

    @Test
    fun `X-Harness-FME-SDK-Thin-Version header sent on evaluations`() = runTest {
        val (client, _, http) = makeClient(sdkVersion = "test-version")

        client.fetchEvaluations(testDefaultTarget, testDefaultFilters, -1L)

        assertEquals("android_thin-test-version", http.lastRequest?.headers?.get("X-Harness-FME-SDK-Thin-Version"))
    }

    @Test
    fun `X-Harness-FME-SDK-Thin-Spec header sent on evaluations`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultFilters, -1L)

        assertEquals(SDK_SPEC_VERSION, http.lastRequest?.headers?.get("X-Harness-FME-SDK-Thin-Spec"))
    }

    @Test
    fun `impressionsMode sent as query param when configured`() = runTest {
        val (client, _, http) = makeClient(impressionsMode = 1)

        client.fetchEvaluations(testDefaultTarget, testDefaultFilters, -1L)

        assertTrue(http.lastRequest?.uri?.query?.contains("impressionsMode=1") == true)
    }

    @Test
    fun `impressionsMode not sent when not configured`() = runTest {
        val (client, _, http) = makeClient(impressionsMode = null)

        client.fetchEvaluations(testDefaultTarget, testDefaultFilters, -1L)

        assertFalse(http.lastRequest?.uri?.query?.contains("impressionsMode") == true)
    }

    @Test
    fun `X-Harness-FME-Content-Digest header sent on evaluations`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultFilters, -1L)

        val digest = http.lastRequest?.headers?.get("X-Harness-FME-Content-Digest")
        assertFalse("X-Harness-FME-Content-Digest header must be present", digest.isNullOrEmpty())
    }

    @Test
    fun `X-Harness-FME-Content-Digest header is deterministic for same target`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultFilters, -1L)
        val first = http.lastRequest?.headers?.get("X-Harness-FME-Content-Digest")

        client.fetchEvaluations(testDefaultTarget, testDefaultFilters, -1L)
        val second = http.lastRequest?.headers?.get("X-Harness-FME-Content-Digest")

        assertEquals(first, second)
    }

    @Test
    fun `X-Harness-FME-Content-Digest header matches ContentDigest utility`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultFilters, -1L)

        val headerDigest = http.lastRequest?.headers?.get("X-Harness-FME-Content-Digest")
        val expectedDigest = ContentDigest.compute(testDefaultTarget)
        assertEquals(expectedDigest, headerDigest)
    }

    @Test
    fun `on 401 invalidates and retries once`() = runTest {
        val firstToken = JwtCredential("first-token", 9999999L, false)
        val secondToken = JwtCredential("second-token", 9999999L, false)
        val auth = FakeAuthProvider(credentialSequence = listOf(firstToken, secondToken))
        val http = FakeRetryableHttpClient(statusCodeSequence = listOf(401, 200))
        val (client, _, _) = makeClient(auth, http)

        client.fetchEvaluations(testDefaultTarget, testDefaultFilters, -1L)

        assertEquals(1, auth.invalidateCallCount) // once for 401
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

        val result = client.fetchEvaluations(testDefaultTarget, testDefaultFilters, -1L)

        assertEquals(2, http.executeCallCount)
        assertEquals(401, result.httpStatus)
    }

    @Test
    fun `non-401 error is returned without retry`() = runTest {
        val http = FakeRetryableHttpClient(statusCode = 500)
        val (client, _, _) = makeClient(httpClient = http)

        val result = client.fetchEvaluations(testDefaultTarget, testDefaultFilters, -1L)

        assertEquals(1, http.executeCallCount)
        assertEquals(500, result.httpStatus)
    }

    @Test
    fun `propagates AuthProvider exception`() = runTest {
        val auth = FakeAuthProvider(throwOnCredential = RuntimeException("auth failed"))
        val (client, _, _) = makeClient(auth)
        var thrown: Throwable? = null

        try {
            client.fetchEvaluations(testDefaultTarget, testDefaultFilters, -1L)
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
            client.fetchEvaluations(testDefaultTarget, testDefaultFilters, -1L)
        } catch (e: RuntimeException) {
            thrown = e
        }

        assertTrue("Expected RuntimeException", thrown is RuntimeException)
        assertEquals("network failed", thrown?.message)
    }

    @Test
    fun `numeric attributes preserved as numbers in body`() = runTest {
        val target = EvaluationTarget(
            matchingKey = "user-1",
            bucketingKey = null,
            attributes = mapOf("count" to 42, "price" to 99.99, "active" to true)
        )
        val (client, _, http) = makeClient()

        client.fetchEvaluations(target, null, -1L)

        val body = http.lastRequest?.body ?: ""
        assertTrue("Body should contain numeric count", body.contains("\"count\":42"))
        assertTrue("Body should contain numeric price", body.contains("\"price\":99.99"))
        assertTrue("Body should contain boolean active", body.contains("\"active\":true"))
    }

    @Test
    fun `string attributes preserved as strings in body`() = runTest {
        val target = EvaluationTarget(
            matchingKey = "user-1",
            bucketingKey = null,
            attributes = mapOf("name" to "John", "email" to "john@example.com")
        )
        val (client, _, http) = makeClient()

        client.fetchEvaluations(target, null, -1L)

        val body = http.lastRequest?.body ?: ""
        assertTrue("Body should contain quoted name", body.contains("\"name\":\"John\""))
        assertTrue("Body should contain quoted email", body.contains("\"email\":\"john@example.com\""))
    }

    @Test
    fun `mixed attribute types preserved correctly in body`() = runTest {
        val target = EvaluationTarget(
            matchingKey = "user-1",
            bucketingKey = null,
            attributes = mapOf("plan" to "premium", "tier" to 3, "premium" to true)
        )
        val (client, _, http) = makeClient()

        client.fetchEvaluations(target, null, -1L)

        val body = http.lastRequest?.body ?: ""
        assertTrue("Body should contain quoted plan", body.contains("\"plan\":\"premium\""))
        assertTrue("Body should contain numeric tier", body.contains("\"tier\":3"))
        assertTrue("Body should contain boolean premium", body.contains("\"premium\":true"))
    }

    @Test
    fun `list attribute preserved as JSON array in body`() = runTest {
        val target = EvaluationTarget(
            matchingKey = "user-1",
            bucketingKey = null,
            attributes = mapOf("tags" to listOf("vip", "beta", "early-access"))
        )
        val (client, _, http) = makeClient()

        client.fetchEvaluations(target, null, -1L)

        val body = http.lastRequest?.body ?: ""
        assertTrue("Body should contain tags array", body.contains("\"tags\":["))
        assertTrue("Body should contain first tag", body.contains("\"vip\""))
        assertTrue("Body should contain second tag", body.contains("\"beta\""))
        assertTrue("Body should contain third tag", body.contains("\"early-access\""))
    }
}
