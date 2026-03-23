package io.split.client.thin.internal.secure

import io.split.client.thin.internal.auth.AuthProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureHttpClientFactoryTest {

    @Test
    fun `createSecureHttpClient returns a SecureHttpClient`() {
        val result = createSecureHttpClient(
            authProvider = FakeAuthProvider(),
            retryableHttpClient = FakeRetryableHttpClient(),
            defaultTarget = testDefaultTarget,
            evaluationsUrl = testEvaluationsUrl,
            eventsUrl = testEventsUrl,
            telemetryUrl = testTelemetryUrl,
            sdkKey = "test-sdk-key",
        )

        assertNotNull(result)
        assertTrue(result is SecureHttpClient)
    }
}
