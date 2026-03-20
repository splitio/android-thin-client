package io.split.client.thin.http

import io.split.android.client.network.HttpClient
import io.split.client.thin.internal.observer.CompositeObserver
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class RetryableHttpClientFactoryTest {

    @Test
    fun `createRetryableHttpClient returns a RetryableHttpClient`() {
        val httpClient = mock(HttpClient::class.java)
        val compositeObserver = mock(CompositeObserver::class.java)

        val result = createRetryableHttpClient(httpClient, compositeObserver)

        assertNotNull(result)
        assertTrue(result is RetryableHttpClient)
    }
}
