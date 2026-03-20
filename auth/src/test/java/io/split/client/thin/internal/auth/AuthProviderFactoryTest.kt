package io.split.client.thin.internal.auth

import io.split.client.thin.http.RetryableHttpClient
import io.split.client.thin.internal.observer.CompositeObserver
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class AuthProviderFactoryTest {

    @Test
    fun `createAuthProvider returns an AuthProvider`() {
        val retryableHttpClient = mock(RetryableHttpClient::class.java)
        val compositeObserver = mock(CompositeObserver::class.java)

        val result = createAuthProvider<TestTarget>(
            retryableHttpClient = retryableHttpClient,
            sdkKey = "test-sdk-key",
            authUrl = "https://auth.example.com",
            compositeObserver = compositeObserver,
        )

        assertNotNull(result)
        assertTrue(result is DefaultAuthProvider<*>)
    }
}
