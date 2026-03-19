package io.split.client.thin.internal.auth

import io.split.android.client.network.HttpResponse
import io.split.client.thin.http.HttpRequestDescriptor
import io.split.client.thin.http.RequestCategory
import io.split.client.thin.http.RetryableHttpClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class DefaultCredentialFetcherTest {

    private val sdkKey = "my-sdk-key"
    private val serviceUrl = "https://auth.split.io"
    private val target = TestTarget("user-1")
    private val stubbedCredential = JwtCredential("token", 9999999L, false)
    private val fakeDeserializer = TokenDeserializer { stubbedCredential }

    private fun makeFetcher(responseJson: String = "{}"): Pair<DefaultCredentialFetcher<TestTarget>, FakeHttpClient> {
        val fakeClient = FakeHttpClient(responseJson)
        return DefaultCredentialFetcher<TestTarget>(fakeClient, sdkKey, serviceUrl, fakeDeserializer) to fakeClient
    }

    @Test
    fun `fetchCredential sends request to correct URI`() = runTest {
        val (fetcher, fakeClient) = makeFetcher()

        fetcher.fetchCredential(target)

        assertEquals("$serviceUrl/v1/auth?users=user-1", fakeClient.lastRequest?.uri?.toString())
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
}

private class FakeHttpClient(private val responseJson: String) : RetryableHttpClient {

    var lastRequest: HttpRequestDescriptor? = null
        private set
    var lastCategory: RequestCategory? = null
        private set

    override suspend fun execute(request: HttpRequestDescriptor, category: RequestCategory): HttpResponse {
        lastRequest = request
        lastCategory = category
        val response = mock(HttpResponse::class.java)
        `when`(response.data).thenReturn(responseJson)
        return response
    }
}
