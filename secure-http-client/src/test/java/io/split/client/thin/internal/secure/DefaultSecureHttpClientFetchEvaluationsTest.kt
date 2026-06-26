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

        client.fetchEvaluations(testDefaultTarget, testDefaultRequest, -1L)

        assertEquals("Bearer my-token", http.lastRequest?.headers?.get("Authorization"))
    }

    @Test
    fun `sends POST to evaluationsUrl`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultRequest, -1L)

        assertTrue(http.lastRequest?.uri?.toString()?.startsWith(testEvaluationsUrl) == true)
    }

    @Test
    fun `uses EVALUATIONS category`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultRequest, -1L)

        assertEquals(RequestCategory.EVALUATIONS, http.lastCategory)
    }

    @Test
    fun `matchingKey sent as key field in body`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultRequest, -1L)

        assertTrue(http.lastRequest?.body?.contains("\"key\":\"user-1\"") == true)
    }

    @Test
    fun `matchingKey NOT in query params`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultRequest, -1L)

        assertFalse(http.lastRequest?.uri?.query?.contains("user=") == true)
    }

    @Test
    fun `bucketingKey sent as bucketingKey field in body when non-null`() = runTest {
        val target = EvaluationTarget(matchingKey = "user-1", bucketingKey = "bucket-key", attributes = null)
        val (client, _, http) = makeClient()

        client.fetchEvaluations(target, testDefaultRequest, -1L)

        assertTrue(http.lastRequest?.body?.contains("\"bucketingKey\":\"bucket-key\"") == true)
    }

    @Test
    fun `bucketingKey is JSON null in body when null`() = runTest {
        val target = EvaluationTarget(matchingKey = "user-1", bucketingKey = null, attributes = null)
        val (client, _, http) = makeClient()

        client.fetchEvaluations(target, testDefaultRequest, -1L)

        assertTrue(http.lastRequest?.body?.contains("\"bucketingKey\":null") == true)
    }

    @Test
    fun `bucketingKey NOT in query params`() = runTest {
        val target = EvaluationTarget(matchingKey = "user-1", bucketingKey = "bk", attributes = null)
        val (client, _, http) = makeClient()

        client.fetchEvaluations(target, testDefaultRequest, -1L)

        assertFalse(http.lastRequest?.uri?.query?.contains("bucketingkey=") == true)
    }

    @Test
    fun `configs true sent in body when request sets configs true`() = runTest {
        val request = EvaluationFilters(sets = emptySet(), configs = true)
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, request, -1L)

        assertTrue(http.lastRequest?.body?.contains("\"configs\":true") == true)
    }

    @Test
    fun `configs false sent in body when request sets configs false`() = runTest {
        val request = EvaluationFilters(sets = emptySet(), configs = false)
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, request, -1L)

        assertTrue(http.lastRequest?.body?.contains("\"configs\":false") == true)
    }

    @Test
    fun `configs NOT in query params`() = runTest {
        val request = EvaluationFilters(sets = emptySet(), configs = true)
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, request, -1L)

        assertFalse(http.lastRequest?.uri?.query?.contains("withconfig") == true)
    }

    @Test
    fun `sets sent as sorted JSON array in body`() = runTest {
        val request = EvaluationFilters(sets = setOf("set-b", "set-a"), configs = false)
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, request, -1L)

        assertTrue(http.lastRequest?.body?.contains("\"sets\":[\"set-a\",\"set-b\"]") == true)
    }

    @Test
    fun `empty sets sends empty array in body`() = runTest {
        val request = EvaluationFilters(sets = emptySet(), configs = false)
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, request, -1L)

        assertTrue(http.lastRequest?.body?.contains("\"sets\":[]") == true)
    }

    @Test
    fun `sets NOT in query params`() = runTest {
        val request = EvaluationFilters(sets = setOf("set-a"), configs = false)
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, request, -1L)

        assertFalse(http.lastRequest?.uri?.query?.contains("sets=") == true)
    }

    @Test
    fun `body top-level keys are in alphabetical order`() = runTest {
        val target = EvaluationTarget(matchingKey = "user-1", bucketingKey = null, attributes = null)
        val (client, _, http) = makeClient()

        client.fetchEvaluations(target, testDefaultRequest, -1L)

        // With no attributes, body is flat — check key order directly by position
        val body = http.lastRequest?.body ?: ""
        val attrPos = body.indexOf("\"attributes\"")
        val bkPos = body.indexOf("\"bucketingKey\"")
        val cfgPos = body.indexOf("\"configs\"")
        val keyPos = body.indexOf("\"key\"")
        val setsPos = body.indexOf("\"sets\"")
        assertTrue("attributes before bucketingKey", attrPos < bkPos)
        assertTrue("bucketingKey before configs", bkPos < cfgPos)
        assertTrue("configs before key", cfgPos < keyPos)
        assertTrue("key before sets", keyPos < setsPos)
    }

    @Test
    fun `attributes sent in body under attributes key`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultRequest, -1L)

        assertTrue(http.lastRequest?.body?.contains("\"attributes\"") == true)
        assertTrue(http.lastRequest?.body?.contains("premium") == true)
    }

    @Test
    fun `changeNumber sent as since query param`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultRequest, 42L)

        assertTrue(http.lastRequest?.uri?.query?.contains("since=42") == true)
    }

    @Test
    fun `changeNumber -1 sent as since=-1`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultRequest, -1L)

        assertTrue(http.lastRequest?.uri?.query?.contains("since=-1") == true)
    }

    @Test
    fun `till query param is absent when targetChangeNumber is null`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultRequest, -1L, targetChangeNumber = null)

        assertFalse(http.lastRequest?.uri?.query?.contains("till=") == true)
    }

    @Test
    fun `till query param is appended when targetChangeNumber is provided`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultRequest, -1L, targetChangeNumber = 42L)

        assertTrue(http.lastRequest?.uri?.query?.contains("till=42") == true)
    }

    @Test
    fun `URI only has since and optionally till — no other params`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultRequest, -1L)

        val paramNames = http.lastRequest?.uri?.query
            ?.split("&")?.map { it.substringBefore("=") } ?: emptyList()
        assertEquals(listOf("since"), paramNames)
    }

    @Test
    fun `X-Harness-FME-SDK-Version header sent on evaluations`() = runTest {
        val (client, _, http) = makeClient(sdkVersion = "test-version")

        client.fetchEvaluations(testDefaultTarget, testDefaultRequest, -1L)

        assertEquals("AndroidThin-test-version", http.lastRequest?.headers?.get("X-Harness-FME-SDK-Version"))
    }

    @Test
    fun `X-Harness-FME-Content-Digest header sent on evaluations`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultRequest, -1L)

        val digest = http.lastRequest?.headers?.get("X-Harness-FME-Content-Digest")
        assertFalse("X-Harness-FME-Content-Digest header must be present", digest.isNullOrEmpty())
    }

    @Test
    fun `X-Harness-FME-Content-Digest header is deterministic for same target and request`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultRequest, -1L)
        val first = http.lastRequest?.headers?.get("X-Harness-FME-Content-Digest")

        client.fetchEvaluations(testDefaultTarget, testDefaultRequest, -1L)
        val second = http.lastRequest?.headers?.get("X-Harness-FME-Content-Digest")

        assertEquals(first, second)
    }

    @Test
    fun `X-Harness-FME-Content-Digest covers full body`() = runTest {
        val (client, _, http) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultRequest, -1L)

        val headerDigest = http.lastRequest?.headers?.get("X-Harness-FME-Content-Digest")
        val body = http.lastRequest?.body ?: ""
        val expectedDigest = ContentDigest.compute(body)
        assertEquals(expectedDigest, headerDigest)
    }

    @Test
    fun `on 401 invalidates and retries once`() = runTest {
        val firstToken = JwtCredential("first-token", 9999999L, false)
        val secondToken = JwtCredential("second-token", 9999999L, false)
        val auth = FakeAuthProvider(credentialSequence = listOf(firstToken, secondToken))
        val http = FakeRetryableHttpClient(statusCodeSequence = listOf(401, 200))
        val (client, _, _) = makeClient(auth, http)

        client.fetchEvaluations(testDefaultTarget, testDefaultRequest, -1L)

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

        val result = client.fetchEvaluations(testDefaultTarget, testDefaultRequest, -1L)

        assertEquals(2, http.executeCallCount)
        assertEquals(401, result.httpStatus)
    }

    @Test
    fun `non-401 error is returned without retry`() = runTest {
        val http = FakeRetryableHttpClient(statusCode = 500)
        val (client, _, _) = makeClient(httpClient = http)

        val result = client.fetchEvaluations(testDefaultTarget, testDefaultRequest, -1L)

        assertEquals(1, http.executeCallCount)
        assertEquals(500, result.httpStatus)
    }

    @Test
    fun `numeric attributes preserved as numbers in body`() = runTest {
        val target = EvaluationTarget(
            matchingKey = "user-1",
            bucketingKey = null,
            attributes = mapOf("count" to 42, "price" to 99.99, "active" to true)
        )
        val (client, _, http) = makeClient()

        client.fetchEvaluations(target, testDefaultRequest, -1L)

        val body = http.lastRequest?.body ?: ""
        assertTrue(body.contains("\"count\":42"))
        assertTrue(body.contains("\"price\":99.99"))
        assertTrue(body.contains("\"active\":true"))
    }

    @Test
    fun `small numeric attribute uses plain decimal not scientific`() = runTest {
        val target = EvaluationTarget(
            matchingKey = "user-1",
            bucketingKey = null,
            attributes = mapOf("discount" to 0.0003)
        )
        val (client, _, http) = makeClient()

        client.fetchEvaluations(target, testDefaultRequest, -1L)

        val body = http.lastRequest?.body ?: ""
        assertTrue(body.contains("\"discount\":0.0003"))
        assertFalse(body.contains("E-"))
    }

    @Test
    fun `NaN attribute is stripped from body`() = runTest {
        val target = EvaluationTarget(
            matchingKey = "user-1",
            bucketingKey = null,
            attributes = mapOf("discount" to Double.NaN)
        )
        val (client, _, http) = makeClient()

        client.fetchEvaluations(target, testDefaultRequest, -1L)

        val body = http.lastRequest?.body ?: ""
        assertFalse(body.contains("discount"))
        assertFalse(body.contains("NaN"))
    }

    @Test
    fun `infinite attribute is stripped from body`() = runTest {
        val target = EvaluationTarget(
            matchingKey = "user-1",
            bucketingKey = null,
            attributes = mapOf("discount" to Double.POSITIVE_INFINITY)
        )
        val (client, _, http) = makeClient()

        client.fetchEvaluations(target, testDefaultRequest, -1L)

        val body = http.lastRequest?.body ?: ""
        assertFalse(body.contains("discount"))
    }

    @Test
    fun `attribute keys are sorted alphabetically in body`() = runTest {
        val target = EvaluationTarget(
            matchingKey = "user-1",
            bucketingKey = null,
            attributes = mapOf("z_attr" to "last", "a_attr" to "first", "m_attr" to "mid"),
        )
        val (client, _, http) = makeClient()

        client.fetchEvaluations(target, testDefaultRequest, -1L)

        val body = http.lastRequest?.body ?: ""
        val aPos = body.indexOf("\"a_attr\"")
        val mPos = body.indexOf("\"m_attr\"")
        val zPos = body.indexOf("\"z_attr\"")
        assertTrue("a_attr before m_attr in body: $body", aPos < mPos)
        assertTrue("m_attr before z_attr in body: $body", mPos < zPos)
    }

    @Test
    fun `list attribute values are sorted alphabetically in body`() = runTest {
        val target = EvaluationTarget(
            matchingKey = "user-1",
            bucketingKey = null,
            attributes = mapOf("tags" to listOf("zzz", "aaa", "mmm")),
        )
        val (client, _, http) = makeClient()

        client.fetchEvaluations(target, testDefaultRequest, -1L)

        val body = http.lastRequest?.body ?: ""
        val aaaPos = body.indexOf("\"aaa\"")
        val mmmPos = body.indexOf("\"mmm\"")
        val zzzPos = body.indexOf("\"zzz\"")
        assertTrue("aaa before mmm in list: $body", aaaPos < mmmPos)
        assertTrue("mmm before zzz in list: $body", mmmPos < zzzPos)
    }

    @Test
    fun `list attribute preserved as JSON array in body`() = runTest {
        val target = EvaluationTarget(
            matchingKey = "user-1",
            bucketingKey = null,
            attributes = mapOf("tags" to listOf("vip", "beta", "early-access"))
        )
        val (client, _, http) = makeClient()

        client.fetchEvaluations(target, testDefaultRequest, -1L)

        val body = http.lastRequest?.body ?: ""
        assertTrue(body.contains("\"tags\":["))
        assertTrue(body.contains("\"vip\""))
        assertTrue(body.contains("\"beta\""))
    }
}
