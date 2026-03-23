package io.split.client.thin.http

import io.split.android.client.network.HttpClient
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class RetryableHttpClientFactoryTest {

    @Test
    fun `createRetryableHttpClient returns a RetryableHttpClient`() {
        val httpClient = mock(HttpClient::class.java)

        val result = createRetryableHttpClient(httpClient)

        assertNotNull(result)
        assertTrue(result is RetryableHttpClient)
    }
}
