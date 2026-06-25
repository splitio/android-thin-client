package io.split.client.thin.internal.auth

import io.split.client.thin.http.HttpRequestDescriptor
import io.split.client.thin.http.RequestCategory
import io.split.client.thin.http.RetryableHttpClient
import io.split.client.thin.http.contracts.HttpResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DefaultCredentialFetcherTest {

    private val sdkKey = "my-sdk-key"
    private val serviceUrl = "https://auth.split.io"
    private val target = "user-1"
    private val stubbedCredential = JwtCredential("token", 9999999L, false)
    private val fakeDeserializer = TokenDeserializer { stubbedCredential }

    private fun makeFetcher(
        responseJson: String = "{}",
        statusCode: Int = 200,
    ): Pair<DefaultCredentialFetcher, FakeHttpClient> {
        val fakeClient = FakeHttpClient(responseJson, statusCode)
        return DefaultCredentialFetcher(fakeClient, sdkKey, serviceUrl, fakeDeserializer) to fakeClient
    }

    @Test
    fun `fetchCredential sends request to correct URI`() = runTest {
        val (fetcher, fakeClient) = makeFetcher()

        fetcher.fetchCredential(setOf(target))

        assertEquals("$serviceUrl?key=user-1", fakeClient.lastRequest?.uri?.toString())
    }

    @Test
    fun `fetchCredential uses AUTH category`() = runTest {
        val (fetcher, fakeClient) = makeFetcher()

        fetcher.fetchCredential(setOf(target))

        assertEquals(RequestCategory.AUTH, fakeClient.lastCategory)
    }

    @Test
    fun `fetchCredential sends Authorization Bearer header`() = runTest {
        val (fetcher, fakeClient) = makeFetcher()

        fetcher.fetchCredential(setOf(target))

        assertEquals("Bearer $sdkKey", fakeClient.lastRequest?.headers?.get("Authorization"))
    }

    @Test
    fun `fetchCredential returns credential from deserializer`() = runTest {
        val (fetcher, _) = makeFetcher()

        val result = fetcher.fetchCredential(setOf(target))

        assertEquals(stubbedCredential, result)
    }

    @Test
    fun `fetchCredential sends repeated key params for multiple targets`() = runTest {
        val (fetcher, fakeClient) = makeFetcher()

        fetcher.fetchCredential(setOf("user-1", "user-2"))

        assertEquals("$serviceUrl?key=user-1&key=user-2", fakeClient.lastRequest?.uri?.toString())
    }

    @Test
    fun `fetchCredential sorts targets before building URI`() = runTest {
        val (fetcher, fakeClient) = makeFetcher()

        fetcher.fetchCredential(setOf("user-2", "user-1"))

        assertEquals("$serviceUrl?key=user-1&key=user-2", fakeClient.lastRequest?.uri?.toString())
    }

    @Test
    fun `fetchCredential URL-encodes target with special characters`() = runTest {
        val specialTarget = "user key&id=1 test"
        val (fetcher, fakeClient) = makeFetcher()

        fetcher.fetchCredential(setOf(specialTarget))

        // URLEncoder encodes space as +, & as %26, = as %3D
        val uri = fakeClient.lastRequest?.uri?.toString() ?: ""
        assertEquals("$serviceUrl?key=user+key%26id%3D1+test", uri)
    }

    @Test
    fun `fetchCredential URL-encodes target with non-ASCII characters`() = runTest {
        val nonAsciiTarget = "usuário"
        val (fetcher, fakeClient) = makeFetcher()

        fetcher.fetchCredential(setOf(nonAsciiTarget))

        val uri = fakeClient.lastRequest?.uri?.toString() ?: ""
        // Must not contain the raw non-ASCII characters
        assertEquals(false, uri.contains("usuário"))
        assertEquals(true, uri.startsWith("$serviceUrl?key="))
    }

    @Test
    fun `fetchCredential does not append configs param`() = runTest {
        val (fetcher, fakeClient) = makeFetcher()

        fetcher.fetchCredential(setOf(target))

        val uri = fakeClient.lastRequest?.uri?.toString() ?: ""
        assertFalse("URI should not contain configs param", uri.contains("configs"))
    }

    @Test
    fun `fetchCredential invokes unauthorized callback on 401 response`() = runTest {
        val fakeClient = FakeHttpClient(responseJson = "{}", statusCode = 401)
        var unauthorizedTarget: String? = null
        val fetcher = DefaultCredentialFetcher(
            retryableHttpClient = fakeClient,
            sdkKey = sdkKey,
            serviceUrl = serviceUrl,
            tokenDeserializer = fakeDeserializer,
            onUnauthorized = { unauthorizedTarget = it },
        )

        try {
            fetcher.fetchCredential(setOf(target))
            fail("Expected auth 401 to throw")
        } catch (_: IllegalStateException) {
            // expected
        }

        assertEquals(target, unauthorizedTarget)
    }

    @Test
    fun `fetchCredential does not deserialize token on 401 response`() = runTest {
        val fakeClient = FakeHttpClient(responseJson = """{"token":"bad"}""", statusCode = 401)
        var deserializeCalls = 0
        val fetcher = DefaultCredentialFetcher(
            retryableHttpClient = fakeClient,
            sdkKey = sdkKey,
            serviceUrl = serviceUrl,
            tokenDeserializer = TokenDeserializer {
                deserializeCalls++
                stubbedCredential
            },
        )

        try {
            fetcher.fetchCredential(setOf(target))
            fail("Expected auth 401 to throw")
        } catch (_: IllegalStateException) {
            // expected
        }

        assertEquals(0, deserializeCalls)
    }
}

private class FakeHttpClient(
    private val responseJson: String,
    private val statusCode: Int,
) : RetryableHttpClient {

    var lastRequest: HttpRequestDescriptor? = null
        private set
    var lastCategory: RequestCategory? = null
        private set

    override suspend fun execute(request: HttpRequestDescriptor, category: RequestCategory): HttpResponse {
        lastRequest = request
        lastCategory = category
        return object : HttpResponse {
            override val isSuccess: Boolean = statusCode in 200..299
            override val httpStatus: Int = statusCode
            override val headers: Map<String, List<String>> = emptyMap()
            override fun getData(): String? = responseJson
        }
    }
}
