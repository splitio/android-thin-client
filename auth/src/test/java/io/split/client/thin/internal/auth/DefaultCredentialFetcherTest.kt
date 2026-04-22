package io.split.client.thin.internal.auth

import io.split.client.thin.http.HttpRequestDescriptor
import io.split.client.thin.http.RequestCategory
import io.split.client.thin.http.RetryableHttpClient
import io.split.client.thin.http.contracts.HttpResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultCredentialFetcherTest {

    private val sdkKey = "my-sdk-key"
    private val serviceUrl = "https://auth.split.io"
    private val target = "user-1"
    private val stubbedCredential = JwtCredential("token", 9999999L, false)
    private val fakeDeserializer = TokenDeserializer { stubbedCredential }

    private fun makeFetcher(responseJson: String = "{}"): Pair<DefaultCredentialFetcher, FakeHttpClient> {
        val fakeClient = FakeHttpClient(responseJson)
        return DefaultCredentialFetcher(fakeClient, sdkKey, serviceUrl, fakeDeserializer) to fakeClient
    }

    @Test
    fun `fetchCredential sends request to correct URI`() = runTest {
        val (fetcher, fakeClient) = makeFetcher()

        fetcher.fetchCredential(target)

        assertEquals("$serviceUrl/?users=user-1", fakeClient.lastRequest?.uri?.toString())
    }

    @Test
    fun `fetchCredential uses AUTH category`() = runTest {
        val (fetcher, fakeClient) = makeFetcher()

        fetcher.fetchCredential(target)

        assertEquals(RequestCategory.AUTH, fakeClient.lastCategory)
    }

    @Test
    fun `fetchCredential sends Authorization Bearer header`() = runTest {
        val (fetcher, fakeClient) = makeFetcher()

        fetcher.fetchCredential(target)

        assertEquals("Bearer $sdkKey", fakeClient.lastRequest?.headers?.get("Authorization"))
    }

    @Test
    fun `fetchCredential returns credential from deserializer`() = runTest {
        val (fetcher, _) = makeFetcher()

        val result = fetcher.fetchCredential(target)

        assertEquals(stubbedCredential, result)
    }

    @Test
    fun `fetchCredential URL-encodes target with special characters`() = runTest {
        val specialTarget = "user key&id=1 test"
        val (fetcher, fakeClient) = makeFetcher()

        fetcher.fetchCredential(specialTarget)

        // URLEncoder encodes space as +, & as %26, = as %3D
        val uri = fakeClient.lastRequest?.uri?.toString() ?: ""
        assertEquals("$serviceUrl/?users=user+key%26id%3D1+test", uri)
    }

    @Test
    fun `fetchCredential URL-encodes target with non-ASCII characters`() = runTest {
        val nonAsciiTarget = "usuário"
        val (fetcher, fakeClient) = makeFetcher()

        fetcher.fetchCredential(nonAsciiTarget)

        val uri = fakeClient.lastRequest?.uri?.toString() ?: ""
        // Must not contain the raw non-ASCII characters
        assertEquals(false, uri.contains("usuário"))
        assertEquals(true, uri.startsWith("$serviceUrl/?users="))
    }
}

private class FakeHttpClient(private val responseJson: String) : RetryableHttpClient {

    var lastRequest: HttpRequestDescriptor? = null
        private set
    var lastCategory: RequestCategory? = null
        private set

    override suspend fun execute(request: HttpRequestDescriptor, category: RequestCategory): HttpResponse {
        lastRequest = request
        lastCategory = category
        return object : HttpResponse {
            override val isSuccess: Boolean = true
            override val httpStatus: Int = 200
            override fun getData(): String? = responseJson
        }
    }
}
