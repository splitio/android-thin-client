package io.split.client.thin.consumer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.split.client.thin.EvaluationOptions
import io.split.client.thin.EvaluationResult
import io.split.client.thin.FallbackTreatment
import io.split.client.thin.FallbackTreatmentsConfiguration
import io.split.client.thin.Key
import io.split.client.thin.SdkKey
import io.split.client.thin.SdkReadyMetadata
import io.split.client.thin.SdkUpdateMetadata
import io.split.client.thin.SplitCallback
import io.split.client.thin.SplitClient
import io.split.client.thin.SplitClientConfig
import io.split.client.thin.SplitEvent
import io.split.client.thin.SplitEventListener
import io.split.client.thin.SplitFactory
import io.split.client.thin.SplitFactoryBuilder
import io.split.client.thin.SplitManager
import io.split.client.thin.SplitVoidCallback
import io.split.client.thin.Target
import io.split.client.thin.fallbackTreatments
import io.split.client.thin.splitClientConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConsumerAndroidTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Given value-type constructors for Key, Target, and SdkKey,
     * When each is instantiated with representative arguments,
     * Then all properties are accessible and hold the expected values.
     */
    @Test
    fun valueTypes() {
        val key = Key("user-123")
        assertEquals("user-123", key.matchingKey)

        val keyWithBucketing = Key("user-123", "bucket-456")
        assertEquals("bucket-456", keyWithBucketing.bucketingKey)

        val target = Target(key = key, trafficType = "user")
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

    /**
     * Given a valid SdkKey and default Target,
     * When a SplitFactory is built and clients/manager are retrieved,
     * Then all returned instances are non-null and setTarget compiles without throwing.
     */
    @Test
    fun factoryBuilderAndClientAccess() {
        val sdkKey = SdkKey("test-sdk-key")
        val target = Target(key = Key("user-1"), trafficType = "user")

        val factory: SplitFactory = SplitFactoryBuilder.build(
            context = context,
            sdkKey = sdkKey,
            defaultTarget = target
        )

        val client: SplitClient = factory.getClient()
        assertNotNull(client)

        val targetedClient: SplitClient = factory.getClient(target)
        assertNotNull(targetedClient)

        val manager: SplitManager = factory.getManager()
        assertNotNull(manager)

        client.setTarget(Target(Key("user-2"), trafficType = "user"))
    }

    /**
     * Given a SplitClientConfig built with a log level,
     * When a SplitFactory is created with that config,
     * Then the factory is non-null.
     */
    @Test
    fun factoryBuilderWithConfig() {
        val config = SplitClientConfig.Builder()
            .logLevel(SplitClientConfig.LogLevel.VERBOSE)
            .build()
        val factory = SplitFactoryBuilder.build(
            context = context,
            sdkKey = SdkKey("key"),
            defaultTarget = Target(key = Key("user"), trafficType = "user"),
            config = config
        )
        assertNotNull(factory)
    }

    /**
     * Given a SplitClient obtained from a factory,
     * When getTreatment, getTreatments, and getTreatmentsByFlagSets are called,
     * Then all returned results are non-null.
     */
    @Test
    fun clientEvaluation() {
        val factory = SplitFactoryBuilder.build(
            context = context,
            sdkKey = SdkKey("key"),
            defaultTarget = Target(key = Key("user"), trafficType = "user")
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

    /**
     * Given a SplitFactory with a default target,
     * When manager.flagNames is accessed,
     * Then the returned list is non-null.
     */
    @Test
    fun managerFlagNames() {
        val factory = SplitFactoryBuilder.build(
            context = context,
            sdkKey = SdkKey("key"),
            defaultTarget = Target(key = Key("user"), trafficType = "user")
        )
        val manager = factory.getManager()
        val names: List<String> = manager.flagNames
        assertNotNull(names)
    }

    /**
     * Given an EvaluationResult constructed with all fields,
     * When each property is accessed,
     * Then the values match what was provided to the constructor.
     */
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

    /**
     * Given the EvaluationOptions class,
     * When instantiated with default arguments,
     * Then the result is non-null.
     */
    @Test
    fun evaluationOptions() {
        val options = EvaluationOptions()
        assertNotNull(options)
    }

    /**
     * Given the splitClientConfig DSL,
     * When all top-level and nested options are set,
     * Then the resulting config is non-null.
     */
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
            }
        }
        assertNotNull(config)
    }

    /**
     * Given the SplitClientConfig.Builder,
     * When all builder methods are chained and build() is called,
     * Then the resulting config is non-null.
     */
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

    /**
     * Given the SplitEvent enum,
     * When all entries are collected,
     * Then SDK_READY, SDK_READY_FROM_CACHE, SDK_READY_TIMEOUT, and SDK_UPDATE are present.
     */
    @Test
    fun splitEventEnum() {
        val events = SplitEvent.entries.toTypedArray()
        assertTrue(events.contains(SplitEvent.SDK_READY))
        assertTrue(events.contains(SplitEvent.SDK_READY_FROM_CACHE))
        assertTrue(events.contains(SplitEvent.SDK_READY_TIMEOUT))
        assertTrue(events.contains(SplitEvent.SDK_UPDATE))
    }

    /**
     * Given a SplitEventListener subclass that overrides onReady,
     * When instantiated,
     * Then the listener is non-null.
     */
    @Test
    fun splitEventListenerSubclass() {
        val listener = object : SplitEventListener() {
            override fun onReady(client: SplitClient, metadata: SdkReadyMetadata?) {
                // no-op
            }
        }
        assertNotNull(listener)
    }

    /**
     * Given a SplitEventListener subclass overriding onReady, onReadyFromCache, and onUpdate,
     * When registered via client.addEventListener,
     * Then no exception is thrown.
     */
    @Test
    fun addEventListenerAcceptsListenerWithoutThrowing() {
        // TODO: fully test once MockWebServer is set up — verify that registered callbacks
        //  (onReady, onReadyFromCache, onUpdate) are actually invoked when the server delivers
        //  the corresponding events.
        val factory = SplitFactoryBuilder.build(
            context = context,
            sdkKey = SdkKey("key"),
            defaultTarget = Target(key = Key("user"), trafficType = "user")
        )
        val client = factory.getClient()
        val listener = object : SplitEventListener() {
            override fun onReady(client: SplitClient, metadata: SdkReadyMetadata?) {}
            override fun onReadyFromCache(client: SplitClient, metadata: SdkReadyMetadata?) {}
            override fun onUpdate(client: SplitClient, metadata: SdkUpdateMetadata?) {}
        }

        client.addEventListener(listener)
        // No exception thrown — listener registration is wired correctly.
    }

    /**
     * Given SdkReadyMetadata constructed with and without arguments,
     * When accessing isInitialCacheLoad and lastUpdateTimestamp,
     * Then values match what was provided to the constructor.
     */
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

    /**
     * Given SdkUpdateMetadata constructed with type and names,
     * When accessing type and names,
     * Then values match and all Type enum entries are accessible.
     */
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

    // -------------------------------------------------------------------------
    // New Phase 0 compilation tests
    // -------------------------------------------------------------------------

    /**
     * Given the FallbackTreatment constructor,
     * When instantiated with treatment and optional config,
     * Then the treatment and config fields hold the provided values.
     */
    @Test
    fun fallbackTreatmentFields() {
        val ft = FallbackTreatment("on", """{"color":"red"}""")
        assertEquals("on", ft.treatment)
        assertEquals("""{"color":"red"}""", ft.config)

        val ftNoConfig = FallbackTreatment("off")
        assertEquals("off", ftNoConfig.treatment)
        assertEquals(null, ftNoConfig.config)
    }

    /**
     * Given FallbackTreatmentsConfiguration.builder(),
     * When global(FallbackTreatment), global(String), byFlag(), and byFlagStrings() are called
     *   followed by build(),
     * Then global and byFlag properties are accessible on the result.
     */
    @Test
    fun fallbackTreatmentsConfigurationBuilder() {
        val config = FallbackTreatmentsConfiguration.builder()
            .global(FallbackTreatment("off"))
            .global("on")
            .byFlag(mapOf("flag-a" to FallbackTreatment("on", """{"k":"v"}""")))
            .byFlagStrings(mapOf("flag-b" to "off"))
            .build()

        assertNotNull(config.global)
        assertEquals("on", config.global?.treatment)
        assertTrue(config.byFlag.containsKey("flag-a"))
        assertTrue(config.byFlag.containsKey("flag-b"))
    }

    /**
     * Given a SplitClient,
     * When track(eventType), track(eventType, value), and track(eventType, value, properties)
     *   are all called,
     * Then none of them throw.
     */
    @Test
    fun trackMethodVariants() {
        val client = SplitFactoryBuilder.build(
            context = context,
            sdkKey = SdkKey("key"),
            defaultTarget = Target(key = Key("user"), trafficType = "user")
        ).getClient()

        client.track("purchase")
        client.track("purchase", 9.99)
        client.track("purchase", 9.99, mapOf("plan" to "premium"))
    }

    /**
     * Given a SplitClient,
     * When destroy() is called inside a coroutine,
     * Then it completes without throwing.
     */
    @Test
    fun clientDestroy() = runTest {
        val client = SplitFactoryBuilder.build(
            context = context,
            sdkKey = SdkKey("key"),
            defaultTarget = Target(key = Key("user"), trafficType = "user")
        ).getClient()

        client.destroy()
    }

    /**
     * Given a SplitClient,
     * When flush() is called inside a coroutine,
     * Then it completes without throwing.
     */
    @Test
    fun clientFlush() = runTest {
        val client = SplitFactoryBuilder.build(
            context = context,
            sdkKey = SdkKey("key"),
            defaultTarget = Target(key = Key("user"), trafficType = "user")
        ).getClient()

        client.flush()
    }

    /**
     * Given a SplitFactory,
     * When destroy() is called inside a coroutine,
     * Then it completes without throwing.
     */
    @Test
    fun factoryDestroy() = runTest {
        val factory = SplitFactoryBuilder.build(
            context = context,
            sdkKey = SdkKey("key"),
            defaultTarget = Target(key = Key("user"), trafficType = "user")
        )

        factory.destroy()
    }

    /**
     * Given SplitCallback<T> and SplitVoidCallback functional interfaces,
     * When used as lambdas,
     * Then they compile and the instances are non-null.
     */
    @Test
    fun callbackFunctionalInterfaces() {
        val callback: SplitCallback<String> = SplitCallback { _, _ ->
            // no-op
        }
        assertNotNull(callback)

        val voidCallback: SplitVoidCallback = SplitVoidCallback { _ ->
            // no-op
        }
        assertNotNull(voidCallback)
    }

    /**
     * Given the ServiceEndpoints constructor,
     * When instantiated with all five fields (including optional streamingUrl),
     * Then each field holds the provided value.
     */
    @Test
    fun serviceEndpointsFields() {
        val endpoints = SplitClientConfig.ServiceEndpoints(
            authUrl = "https://auth.example.com",
            evaluationsUrl = "https://eval.example.com",
            eventsUrl = "https://events.example.com",
            telemetryUrl = "https://telemetry.example.com",
            streamingUrl = "https://streaming.example.com",
        )

        assertEquals("https://auth.example.com", endpoints.authUrl)
        assertEquals("https://eval.example.com", endpoints.evaluationsUrl)
        assertEquals("https://events.example.com", endpoints.eventsUrl)
        assertEquals("https://telemetry.example.com", endpoints.telemetryUrl)
        assertEquals("https://streaming.example.com", endpoints.streamingUrl)
    }

    /**
     * Given a SplitEventListener subclass,
     * When onTimeout and onTimeoutView are overridden,
     * Then the subclass compiles and is non-null.
     */
    @Test
    fun eventListenerTimeoutCallbacks() {
        val listener = object : SplitEventListener() {
            override fun onTimeout(client: SplitClient) {
                // no-op
            }

            override fun onTimeoutView(client: SplitClient) {
                // no-op
            }
        }
        assertNotNull(listener)
    }

    /**
     * Given a SplitEventListener subclass,
     * When onReadyView, onUpdateView, and onReadyFromCacheView are overridden,
     * Then the subclass compiles and is non-null.
     */
    @Test
    fun eventListenerViewCallbacks() {
        val listener = object : SplitEventListener() {
            override fun onReadyView(client: SplitClient, metadata: SdkReadyMetadata?) {
                // no-op
            }

            override fun onUpdateView(client: SplitClient, metadata: SdkUpdateMetadata?) {
                // no-op
            }

            override fun onReadyFromCacheView(client: SplitClient, metadata: SdkReadyMetadata?) {
                // no-op
            }
        }
        assertNotNull(listener)
    }

    /**
     * Given the splitClientConfig DSL with a fallbackTreatments block,
     * When global("off") and byFlagStrings() are called inside the block,
     * Then the resulting config has the expected fallback treatments.
     */
    @Test
    fun fallbackTreatmentsConfigDsl() {
        val config = splitClientConfig {
            fallbackTreatments {
                global("off")
                byFlagStrings(mapOf("my-flag" to "on"))
            }
        }

        assertNotNull(config.fallbackTreatments)
        assertEquals("off", config.fallbackTreatments?.global?.treatment)
        assertTrue(config.fallbackTreatments?.byFlag?.containsKey("my-flag") == true)
    }

    /**
     * Given a SplitClientConfig.Builder with a FallbackTreatmentsConfiguration,
     * When build() is called,
     * Then the resulting config has a non-null fallbackTreatments property.
     */
    @Test
    fun configWithFallbackTreatmentsBuilder() {
        val fallback = FallbackTreatmentsConfiguration.builder()
            .global("off")
            .build()

        val config = SplitClientConfig.Builder()
            .fallbackTreatments(fallback)
            .build()

        assertNotNull(config.fallbackTreatments)
        assertEquals("off", config.fallbackTreatments?.global?.treatment)
    }
}
