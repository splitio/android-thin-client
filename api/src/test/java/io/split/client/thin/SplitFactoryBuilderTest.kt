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
        SplitFactoryBuilder.buildInternal(
            context = mockContext,
            sdkKey = sdkKey,
            defaultTarget = defaultTarget,
            config = config,
            configChangeDetectorFactory = { false },
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
        var capturedConfig: io.split.client.thin.internal.persistence.domain.PersistenceConfig? = null
        val config = splitClientConfig { storage { prefix = "my_app" } }

        SplitFactoryBuilder.buildInternal(
            context = mockContext,
            sdkKey = sdkKey,
            defaultTarget = defaultTarget,
            config = config,
            configChangeDetectorFactory = { false },
            persistenceConfigCapture = { capturedConfig = it },
        ).also { createdFactories.add(it) }

        assertEquals("my_app", capturedConfig?.prefix)
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

    @Test
    fun `buildCacheLoadedPayload returns SdkReadyMetadata with isInitialCacheLoad false`() {
        val result = buildCacheLoadedPayload(lastUpdateTimestamp = 12345L)
        assertEquals(false, result.isInitialCacheLoad)
        assertEquals(12345L, result.lastUpdateTimestamp)
    }

    @Test
    fun `buildCacheLoadedPayload passes null lastUpdateTimestamp`() {
        val result = buildCacheLoadedPayload(lastUpdateTimestamp = null)
        assertEquals(false, result.isInitialCacheLoad)
        assertNull(result.lastUpdateTimestamp)
    }

    @Test
    fun `buildEvaluationsUpdatedPayload INITIALIZATION no cache returns isInitialCacheLoad true`() {
        val result = buildEvaluationsUpdatedPayload(
            reason = io.split.client.thin.internal.evaluation.FetchReason.INITIALIZATION,
            changedFlagNames = emptyList(),
            isCacheLoaded = false
        ) as SdkReadyMetadata
        assertEquals(true, result.isInitialCacheLoad)
        assertNull(result.lastUpdateTimestamp)
    }

    @Test
    fun `buildEvaluationsUpdatedPayload INITIALIZATION with cache returns isInitialCacheLoad false`() {
        val result = buildEvaluationsUpdatedPayload(
            reason = io.split.client.thin.internal.evaluation.FetchReason.INITIALIZATION,
            changedFlagNames = emptyList(),
            isCacheLoaded = true
        ) as SdkReadyMetadata
        assertEquals(false, result.isInitialCacheLoad)
    }

    @Test
    fun `buildEvaluationsUpdatedPayload PERIODIC with flags returns SdkUpdateMetadata`() {
        val result = buildEvaluationsUpdatedPayload(
            reason = io.split.client.thin.internal.evaluation.FetchReason.PERIODIC,
            changedFlagNames = listOf("flag-a", "flag-b"),
            isCacheLoaded = true
        ) as SdkUpdateMetadata
        assertEquals(SdkUpdateMetadata.Type.FLAGS_UPDATE, result.type)
        assertEquals(listOf("flag-a", "flag-b"), result.names)
    }

    @Test
    fun `buildEvaluationsUpdatedPayload PERIODIC empty flags returns null`() {
        val result = buildEvaluationsUpdatedPayload(
            reason = io.split.client.thin.internal.evaluation.FetchReason.PERIODIC,
            changedFlagNames = emptyList(),
            isCacheLoaded = true
        )
        assertNull(result)
    }
}
