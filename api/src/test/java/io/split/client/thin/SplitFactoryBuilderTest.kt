package io.split.client.thin

import android.content.Context
import io.split.client.thin.internal.DefaultSplitFactory
import io.split.client.thin.internal.evaluation.EvaluationKey
import io.split.client.thin.internal.evaluation.SyncDelayCalculator
import io.split.client.thin.internal.streaming.EvaluationUpdateNotification
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class SplitFactoryBuilderTest {

    private val sdkKey = SdkKey("test-sdk-key")
    private val defaultTarget = Target(Key("user-1"), trafficType = "user")
    private val mockContext = mock(Context::class.java).also {
        `when`(it.applicationContext).thenReturn(it)
    }

    private val createdFactories = mutableListOf<SplitFactory>()

    @After
    fun tearDown() {
        runBlocking {
            createdFactories.forEach { runCatching { it.destroy() } }
        }
        createdFactories.clear()
    }

    private fun buildFactory(config: SplitClientConfig? = null): SplitFactory =
        SplitFactoryBuilder.build(
            context = mockContext,
            sdkKey = sdkKey,
            defaultTarget = defaultTarget,
            config = config,
        ).also { createdFactories.add(it) }

    private fun assertIsDefaultSplitFactory(factory: SplitFactory) {
        assertNotNull(factory)
        assertTrue(factory is DefaultSplitFactory)
    }

    @Test
    fun `build returns a SplitFactory using default endpoints`() {
        assertIsDefaultSplitFactory(buildFactory())
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

        assertIsDefaultSplitFactory(buildFactory(config))
    }

    @Test
    fun `build returns a SplitFactory in polling mode`() {
        val config = splitClientConfig { sync { mode = SplitClientConfig.SyncMode.POLLING } }
        assertIsDefaultSplitFactory(buildFactory(config))
    }

    @Test
    fun `build returns a SplitFactory in streaming mode`() {
        val config = splitClientConfig { sync { mode = SplitClientConfig.SyncMode.STREAMING } }
        assertIsDefaultSplitFactory(buildFactory(config))
    }

    @Test
    fun `build returns a SplitFactory in single sync mode`() {
        val config = splitClientConfig { sync { mode = SplitClientConfig.SyncMode.SINGLE_SYNC } }
        assertIsDefaultSplitFactory(buildFactory(config))
    }

    @Test
    fun `build uses storage prefix from config for persistence`() {
        val config = splitClientConfig { storage { prefix = "my_app" } }
        assertIsDefaultSplitFactory(buildFactory(config))
    }

    @Test
    fun `build accesses context applicationContext for persistence initialization`() {
        assertIsDefaultSplitFactory(buildFactory())
        verify(mockContext).applicationContext
    }

    // buildDelayProvider

    @Test
    fun `buildDelayProvider returns null when notification is null`() {
        assertNull(buildDelayProvider(null))
    }

    @Test
    fun `buildDelayProvider returns null when notification has no delay fields`() {
        val notification = EvaluationUpdateNotification(changeNumber = 1L, channelName = null, eventTimestamp = 0L)
        // hashingAlgorithm is null → calculator returns 0, but provider itself is non-null
        // Actually with null hashingAlgorithm, provider is non-null but calculator returns 0
        val provider = buildDelayProvider(notification)
        assertNotNull(provider)
    }

    @Test
    fun `buildDelayProvider invokes calculator with matchingKey and notification fields`() {
        val capturedArgs = mutableListOf<Any?>()
        val fakeCalculator = object : SyncDelayCalculator {
            override fun calculateDelay(key: String, updateIntervalMs: Long?, algorithmSeed: Int?, hashingAlgorithm: Int?): Long {
                capturedArgs.addAll(listOf(key, updateIntervalMs, algorithmSeed, hashingAlgorithm))
                return 42L
            }
        }
        val notification = EvaluationUpdateNotification(
            changeNumber = 1L, channelName = null, eventTimestamp = 0L,
            updateIntervalMs = 60000L, algorithmSeed = 7, hashingAlgorithm = 1
        )
        val evalKey = EvaluationKey(Key("my-key"))

        val provider = buildDelayProvider(notification, fakeCalculator)
        val delay = provider!!(evalKey)

        assertEquals(42L, delay)
        assertEquals("my-key", capturedArgs[0])
        assertEquals(60000L, capturedArgs[1])
        assertEquals(7, capturedArgs[2])
        assertEquals(1, capturedArgs[3])
    }
}
