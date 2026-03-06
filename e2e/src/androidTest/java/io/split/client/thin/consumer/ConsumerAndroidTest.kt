package io.split.client.thin.consumer

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.split.client.thin.EvaluationOptions
import io.split.client.thin.EvaluationResult
import io.split.client.thin.Key
import io.split.client.thin.SdkKey
import io.split.client.thin.SdkReadyMetadata
import io.split.client.thin.SdkUpdateMetadata
import io.split.client.thin.SplitClient
import io.split.client.thin.SplitClientConfig
import io.split.client.thin.SplitEvent
import io.split.client.thin.SplitEventListener
import io.split.client.thin.SplitFactory
import io.split.client.thin.SplitFactoryBuilder
import io.split.client.thin.SplitManager
import io.split.client.thin.Target
import io.split.client.thin.splitClientConfig
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConsumerAndroidTest {

    @Test
    fun valueTypes() {
        val key = Key("user-123")
        assertEquals("user-123", key.matchingKey)

        val keyWithBucketing = Key("user-123", "bucket-456")
        assertEquals("bucket-456", keyWithBucketing.bucketingKey)

        val target = Target(key = key)
        assertEquals(key, target.key)
        assertTrue(target.attributes.isEmpty())

        val targetWithAttrs = Target(
            key = key,
            attributes = mapOf("plan" to "premium"),
            trafficType = "user"
        )
        assertEquals("premium", targetWithAttrs.attributes["plan"])
        assertEquals("user", targetWithAttrs.trafficType)

        val sdkKey = SdkKey("sdk-key-value")
        assertEquals("sdk-key-value", sdkKey.sdkKey)
    }

    @Test
    fun factoryBuilderAndClientAccess() {
        val sdkKey = SdkKey("test-sdk-key")
        val target = Target(key = Key("user-1"))

        val factory: SplitFactory = SplitFactoryBuilder.build(
            sdkKey = sdkKey,
            defaultTarget = target
        )

        val client: SplitClient = factory.getClient()
        assertNotNull(client)

        val targetedClient: SplitClient = factory.getClient(target)
        assertNotNull(targetedClient)

        val manager: SplitManager = factory.getManager()
        assertNotNull(manager)

        runTest {
            client.setTarget(Target(Key("user-2")))
        }
    }

    @Test
    fun factoryBuilderWithConfig() {
        val config = SplitClientConfig.Builder()
            .logLevel(SplitClientConfig.LogLevel.VERBOSE)
            .build()
        val factory = SplitFactoryBuilder.build(
            sdkKey = SdkKey("key"),
            defaultTarget = Target(key = Key("user")),
            config = config
        )
        assertNotNull(factory)
    }

    @Test
    fun clientEvaluation() {
        val factory = SplitFactoryBuilder.build(
            sdkKey = SdkKey("key"),
            defaultTarget = Target(key = Key("user"))
        )
        val client = factory.getClient()

        val result: EvaluationResult = client.getTreatment("my-flag")
        assertNotNull(result)

        val resultWithOptions: EvaluationResult =
            client.getTreatment("my-flag", EvaluationOptions())
        assertNotNull(resultWithOptions)

        val results: List<EvaluationResult> =
            client.getTreatments(listOf("flag-1", "flag-2"))
        assertNotNull(results)

        val resultsByFlagSets: List<EvaluationResult> =
            client.getTreatmentsByFlagSets(listOf("set-a"))
        assertNotNull(resultsByFlagSets)
    }

    @Test
    fun managerFlagNames() {
        val factory = SplitFactoryBuilder.build(
            sdkKey = SdkKey("key"),
            defaultTarget = Target(key = Key("user"))
        )
        val manager = factory.getManager()
        val names: List<String> = manager.flagNames
        assertNotNull(names)
    }

    @Test
    fun evaluationResultFields() {
        val result = EvaluationResult(
            flag = "my-flag",
            treatment = "on",
            config = """{"color":"red"}""",
            label = "default rule",
            changeNumber = 42L
        )
        assertEquals("my-flag", result.flag)
        assertEquals("on", result.treatment)
        assertEquals("""{"color":"red"}""", result.config)
        assertEquals("default rule", result.label)
        assertEquals(42L, result.changeNumber)
    }

    @Test
    fun evaluationOptions() {
        val options = EvaluationOptions()
        assertNotNull(options)
    }

    @Test
    fun splitClientConfigDsl() {
        val config: SplitClientConfig = splitClientConfig {
            logLevel = SplitClientConfig.LogLevel.DEBUG
            impressionsMode = SplitClientConfig.ImpressionsMode.NONE
            dynamicConfig = true
            sync {
                mode = SplitClientConfig.SyncMode.POLLING
                evaluationRefreshRate = 120
                pushRate = 60
            }
            storage {
                prefix = "test"
                timeout = 5000
            }
        }
        assertNotNull(config)
    }

    @Test
    fun splitClientConfigBuilder() {
        val config = SplitClientConfig.Builder()
            .logLevel(SplitClientConfig.LogLevel.WARN)
            .impressionsMode(SplitClientConfig.ImpressionsMode.DEFAULT)
            .dynamicConfig(false)
            .sync(
                SplitClientConfig.SyncConfig.Builder()
                    .mode(SplitClientConfig.SyncMode.STREAMING)
                    .build()
            )
            .storage(
                SplitClientConfig.StorageConfig.Builder()
                    .prefix("myprefix")
                    .build()
            )
            .build()
        assertNotNull(config)
    }

    @Test
    fun splitEventEnum() {
        val events = SplitEvent.entries.toTypedArray()
        assertTrue(events.contains(SplitEvent.SDK_READY))
        assertTrue(events.contains(SplitEvent.SDK_READY_FROM_CACHE))
        assertTrue(events.contains(SplitEvent.SDK_READY_TIMEOUT))
        assertTrue(events.contains(SplitEvent.SDK_UPDATE))
    }

    @Test
    fun splitEventListenerSubclass() {
        val listener = object : SplitEventListener() {
            override fun onReady(client: SplitClient, metadata: SdkReadyMetadata) {
                // no-op
            }
        }
        assertNotNull(listener)
    }

    @Test
    fun sdkReadyMetadata() {
        val meta = SdkReadyMetadata()
        assertNotNull(meta)

        val metaWithFields = SdkReadyMetadata(
            isInitialCacheLoad = true,
            lastUpdateTimestamp = 1234567890L
        )
        assertEquals(true, metaWithFields.isInitialCacheLoad)
        assertEquals(1234567890L, metaWithFields.lastUpdateTimestamp)
    }

    @Test
    fun sdkUpdateMetadata() {
        val meta = SdkUpdateMetadata()
        assertNotNull(meta)

        val metaWithType = SdkUpdateMetadata(
            type = SdkUpdateMetadata.Type.FLAGS_UPDATE,
            names = listOf("flag-1")
        )
        assertEquals(SdkUpdateMetadata.Type.FLAGS_UPDATE, metaWithType.type)
        assertEquals(listOf("flag-1"), metaWithType.names)

        // Verify all enum values
        assertNotNull(SdkUpdateMetadata.Type.FLAGS_UPDATE)
        assertNotNull(SdkUpdateMetadata.Type.SEGMENTS_UPDATE)
    }
}
