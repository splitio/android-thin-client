package io.split.client.thin.internal.secure

import io.split.client.thin.http.RequestCategory
import io.split.client.thin.internal.auth.JwtCredential
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultSecureHttpClientPostEventsTest {

    @Test
    fun `sends POST to eventsUrl`() = runTest {
        val (client, _, http) = makeClient()

        client.postEvents("events-payload")

        assertEquals(testEventsUrl, http.lastRequest?.uri?.toString())
    }

    @Test
    fun `uses EVENTS category`() = runTest {
        val (client, _, http) = makeClient()

        client.postEvents("events-payload")

        assertEquals(RequestCategory.EVENTS, http.lastCategory)
    }

    @Test
    fun `sends payload as body`() = runTest {
        val (client, _, http) = makeClient()

        client.postEvents("my-events-data")

        assertEquals("my-events-data", http.lastRequest?.body)
    }

    @Test
    fun `uses default target for JWT`() = runTest {
        val auth = FakeAuthProvider()
        val (client, _, _) = makeClient(auth)

        client.postEvents("payload")

        assertEquals(testDefaultTarget, auth.lastCredentialTarget)
    }

    @Test
    fun `injects Authorization Bearer header`() = runTest {
        val jwt = JwtCredential("events-token", 9999999L, false)
        val auth = FakeAuthProvider(credential = jwt)
        val (client, _, http) = makeClient(auth)

        client.postEvents("payload")

        assertEquals("Bearer events-token", http.lastRequest?.headers?.get("Authorization"))
    }

    @Test
    fun `on 401 invalidates and retries once`() = runTest {
        val auth = FakeAuthProvider(credentialSequence = listOf(
            JwtCredential("t1", 9999999L, false),
            JwtCredential("t2", 9999999L, false),
        ))
        val http = FakeRetryableHttpClient(statusCodeSequence = listOf(401, 200))
        val (client, _, _) = makeClient(auth, http)

        client.postEvents("payload")

        assertEquals(1, auth.invalidateCallCount)
        assertEquals(2, http.executeCallCount)
    }
}
