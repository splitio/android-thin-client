package io.split.client.thin.internal.auth

import io.split.android.client.network.HttpResponse
import io.split.client.thin.http.HttpRequestDescriptor
import io.split.client.thin.http.RequestCategory
import io.split.client.thin.http.RetryableHttpClient
import io.split.client.thin.internal.observer.CompositeObserver
import io.split.client.thin.internal.observer.ObservableEvent
import io.split.client.thin.internal.observer.ObservableEventType
import io.split.client.thin.internal.observer.Observer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class AuthProviderFactoryTest {

    private val capturedEvents = mutableListOf<ObservableEvent>()
    private val fakeObserver = object : CompositeObserver {
        override fun notifyEvent(event: ObservableEvent) { capturedEvents.add(event) }
        override fun register(observer: Observer) {}
        override fun unregisterAll() {}
    }

    @Before
    fun setUp() {
        capturedEvents.clear()
    }

    private fun makeProvider(httpClient: RetryableHttpClient = FakeSuccessHttpClient()) =
        createAuthProvider(
            retryableHttpClient = httpClient,
            sdkKey = "test-sdk-key",
            authUrl = "https://auth.example.com",
            compositeObserver = fakeObserver,
            compositeKeyBuilder = { targets -> targets.sorted().joinToString(",") },
        )

    @Test
    fun `createAuthProvider returns a DefaultAuthProvider`() {
        assertNotNull(makeProvider())
        assertTrue(makeProvider() is DefaultAuthProvider)
    }

    @Test
    fun `observer receives JWT_REQUEST_STARTED and JWT_FETCH_STARTED and JWT_FETCH_SUCCEEDED and JWT_STORED on first fetch`() = runTest {
        makeProvider().credential(setOf("user-1"))

        val types = capturedEvents.map { it.type }
        assertTrue(types.contains(ObservableEventType.JWT_REQUEST_STARTED))
        assertTrue(types.contains(ObservableEventType.JWT_FETCH_STARTED))
        assertTrue(types.contains(ObservableEventType.JWT_FETCH_SUCCEEDED))
        assertTrue(types.contains(ObservableEventType.JWT_STORED))
    }

    @Test
    fun `observer receives JWT_RETURNED_FROM_STORAGE on second credential fetch`() = runTest {
        val provider = makeProvider()
        provider.credential(setOf("user-1"))
        capturedEvents.clear()

        provider.credential(setOf("user-1"))

        assertTrue(capturedEvents.any { it.type == ObservableEventType.JWT_RETURNED_FROM_STORAGE })
    }

    @Test
    fun `observer receives JWT_FETCH_FAILED_NON_RETRYABLE on HTTP failure`() = runTest {
        val provider = makeProvider(FakeFailingHttpClient())
        try {
            provider.credential(setOf("user-1"))
        } catch (_: Exception) {}

        assertTrue(capturedEvents.any { it.type == ObservableEventType.JWT_FETCH_FAILED_NON_RETRYABLE })
    }

    @Test
    fun `events include matching key from target`() = runTest {
        makeProvider().credential(setOf("user-1"))

        val requestStarted = capturedEvents.first { it.type == ObservableEventType.JWT_REQUEST_STARTED }
        assertEquals("user-1", requestStarted.properties["matchingKey"])
    }
}

private class FakeSuccessHttpClient : RetryableHttpClient {
    override suspend fun execute(request: HttpRequestDescriptor, category: RequestCategory): HttpResponse {
        val response = mock(HttpResponse::class.java)
        `when`(response.data).thenReturn("""{"token":"header.payload.sig","pushEnabled":false}""")
        return response
    }
}

private class FakeFailingHttpClient : RetryableHttpClient {
    override suspend fun execute(request: HttpRequestDescriptor, category: RequestCategory): HttpResponse {
        throw RuntimeException("HTTP failure")
    }
}
