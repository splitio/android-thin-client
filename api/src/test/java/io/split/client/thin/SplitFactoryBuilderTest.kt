package io.split.client.thin

import io.split.android.client.network.HttpClient
import io.split.client.thin.internal.DefaultSplitFactory
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class SplitFactoryBuilderTest {

    private val httpClient = mock(HttpClient::class.java)
    private val sdkKey = SdkKey("test-sdk-key")
    private val defaultTarget = Target(Key("user-1"))

    @Test
    fun `build returns a SplitFactory using default endpoints`() {
        val factory = SplitFactoryBuilder.build(
            sdkKey = sdkKey,
            defaultTarget = defaultTarget,
            httpClient = httpClient,
        )

        assertNotNull(factory)
        assertTrue(factory is DefaultSplitFactory)
    }

    @Test
    fun `build returns a SplitFactory using configured endpoints`() {
        val config = splitClientConfig {
            sync {
                serviceEndpoints {
                    authUrl = "https://auth.example.com"
                    evaluationsUrl = "https://evaluations.example.com"
                    eventsUrl = "https://events.example.com"
                    telemetryUrl = "https://telemetry.example.com"
                }
            }
        }

        val factory = SplitFactoryBuilder.build(
            sdkKey = sdkKey,
            defaultTarget = defaultTarget,
            config = config,
            httpClient = httpClient,
        )

        assertNotNull(factory)
        assertTrue(factory is DefaultSplitFactory)
    }
}
