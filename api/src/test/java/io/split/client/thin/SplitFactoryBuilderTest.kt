package io.split.client.thin

import android.content.Context
import io.split.client.thin.internal.DefaultSplitFactory
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class SplitFactoryBuilderTest {

    private val sdkKey = SdkKey("test-sdk-key")
    private val defaultTarget = Target(Key("user-1"))
    private val mockContext = mock(Context::class.java).also {
        `when`(it.applicationContext).thenReturn(it)
    }

    @Test
    fun `build returns a SplitFactory using default endpoints`() {
        val factory = SplitFactoryBuilder.build(
            context = mockContext,
            sdkKey = sdkKey,
            defaultTarget = defaultTarget,
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
            context = mockContext,
            sdkKey = sdkKey,
            defaultTarget = defaultTarget,
            config = config,
        )

        assertNotNull(factory)
        assertTrue(factory is DefaultSplitFactory)
    }

    @Test
    fun `build returns a SplitFactory in polling mode`() {
        val config = splitClientConfig {
            sync {
                mode = SplitClientConfig.SyncMode.POLLING
            }
        }

        val factory = SplitFactoryBuilder.build(
            context = mockContext,
            sdkKey = sdkKey,
            defaultTarget = defaultTarget,
            config = config,
        )

        assertNotNull(factory)
        assertTrue(factory is DefaultSplitFactory)
    }

    @Test
    fun `build uses storage prefix from config for persistence`() {
        val config = splitClientConfig {
            storage {
                prefix = "my_app"
            }
        }

        val factory = SplitFactoryBuilder.build(
            context = mockContext,
            sdkKey = sdkKey,
            defaultTarget = defaultTarget,
            config = config,
        )

        assertNotNull(factory)
        assertTrue(factory is DefaultSplitFactory)
    }

    @Test
    fun `build accesses context applicationContext for persistence initialization`() {
        val factory = SplitFactoryBuilder.build(
            context = mockContext,
            sdkKey = sdkKey,
            defaultTarget = defaultTarget,
        )

        assertNotNull(factory)
        assertTrue(factory is DefaultSplitFactory)
        // Verify that context.applicationContext was accessed during build
        verify(mockContext).applicationContext
    }
}
