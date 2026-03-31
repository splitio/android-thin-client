package io.split.client.thin.internal.secure

import io.split.client.thin.http.RequestCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `injects SDK key as Authorization Bearer header`() = runTest {
        val (client, _, http) = makeClient(sdkKey = "my-sdk-key")

        client.postEvents("payload")

        assertEquals("Bearer my-sdk-key", http.lastRequest?.headers?.get("Authorization"))
    }

    @Test
    fun `does not call auth provider`() = runTest {
        val auth = FakeAuthProvider()
        val (client, _, _) = makeClient(auth)

        client.postEvents("payload")

        assertEquals(0, auth.credentialCallCount)
    }

    @Test
    fun `Content-Type header is set to application json`() = runTest {
        val (client, _, http) = makeClient()

        client.postEvents("payload")

        assertEquals("application/json", http.lastRequest?.headers?.get("Content-Type"))
    }

    @Test
    fun `Accept header is set to application json`() = runTest {
        val (client, _, http) = makeClient()

        client.postEvents("payload")

        assertEquals("application/json", http.lastRequest?.headers?.get("Accept"))
    }

    @Test
    fun `SplitSDKVersion header sent on events`() = runTest {
        val (client, _, http) = makeClient(sdkVersion = "1.2.3")

        client.postEvents("payload")

        assertEquals("android-thin-1.2.3", http.lastRequest?.headers?.get("SplitSDKVersion"))
    }

    @Test
    fun `X-Harness-FME-SDK-Thin-Version header sent on events`() = runTest {
        val (client, _, http) = makeClient(sdkVersion = "2.0.0")

        client.postEvents("payload")

        assertEquals("android-thin-2.0.0", http.lastRequest?.headers?.get("X-Harness-FME-SDK-Thin-Version"))
    }

    @Test
    fun `SDK version headers not sent on events`() = runTest {
        val (client, _, http) = makeClient()

        client.postEvents("payload")

        assertNull(http.lastRequest?.headers?.get("X-Harness-FME-SDK-Thin-Spec"))
    }

}
