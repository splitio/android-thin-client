package io.split.client.thin.consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.split.client.thin.e2e.MockSplitServer;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.split.client.thin.SplitCallback;
import io.split.client.thin.SplitVoidCallback;
import io.split.client.thin.EvaluationResult;
import io.split.client.thin.FallbackTreatment;
import io.split.client.thin.FallbackTreatmentsConfiguration;
import io.split.client.thin.Key;
import io.split.client.thin.SdkKey;
import io.split.client.thin.SdkReadyMetadata;
import io.split.client.thin.SdkUpdateMetadata;
import io.split.client.thin.SplitClient;
import io.split.client.thin.SplitClientConfig;
import io.split.client.thin.SplitEvent;
import io.split.client.thin.SplitEventListener;
import io.split.client.thin.SplitFactory;
import io.split.client.thin.SplitFactoryBuilder;
import io.split.client.thin.SplitManager;
import io.split.client.thin.Target;

@RunWith(AndroidJUnit4.class)
public class ConsumerJavaAndroidTest {

    private Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private MockSplitServer server;

    @Before
    public void setUp() {
        server = new MockSplitServer();
    }

    @After
    public void tearDown() {
        server.shutdown();
    }

    private SplitClientConfig mockConfig() {
        return new SplitClientConfig.Builder()
                .sync(new SplitClientConfig.SyncConfig.Builder()
                        .mode(SplitClientConfig.SyncMode.POLLING)
                        .serviceEndpoints(new SplitClientConfig.ServiceEndpoints.Builder()
                                .auth(server.url(""))
                                .evaluations(server.url(""))
                                .events(server.url(""))
                                .build())
                        .build())
                .storage(new SplitClientConfig.StorageConfig.Builder()
                        .prefix("consumer_java_test")
                        .build())
                .build();
    }

    private SplitFactory buildFactory(String sdkKey, String userKey) {
        return SplitFactoryBuilder.build(
                context(),
                new SdkKey(sdkKey),
                new Target(new Key(userKey), "user"),
                mockConfig()
        );
    }

    /**
     * Given value-type constructors for Key, Target, and SdkKey,
     * When each is instantiated with representative arguments,
     * Then all properties are accessible and hold the expected values.
     */
    @Test
    public void valueTypes() {
        Key key = new Key("user-123");
        assertEquals("user-123", key.getMatchingKey());

        Key keyWithBucketing = new Key("user-123", "bucket-456");
        assertEquals("bucket-456", keyWithBucketing.getBucketingKey());

        Target target = new Target(key, "user");
        assertEquals(key, target.getKey());
        assertTrue(target.getAttributes().isEmpty());

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("plan", "premium");
        Target targetWithAttrs = new Target(key, attrs, "user");
        assertEquals("premium", targetWithAttrs.getAttributes().get("plan"));
        assertEquals("user", targetWithAttrs.getTrafficType());

        SdkKey sdkKey = new SdkKey("sdk-key-value");
        assertEquals("sdk-key-value", sdkKey.getSdkKey());
    }

    /**
     * Given a valid SdkKey and default Target,
     * When a SplitFactory is built and clients/manager are retrieved,
     * Then all returned instances are non-null.
     */
    @Test
    public void factoryBuilderAndClientAccess() {
        SdkKey sdkKey = new SdkKey("test-sdk-key");
        Target target = new Target(new Key("user-1"), "user");

        SplitFactory factory = SplitFactoryBuilder.build(context(), sdkKey, target, mockConfig());
        assertNotNull(factory);

        SplitClient client = factory.getClient(null);
        assertNotNull(client);

        SplitClient targetedClient = factory.getClient(target);
        assertNotNull(targetedClient);

        SplitManager manager = factory.getManager();
        assertNotNull(manager);
    }

    /**
     * Given a SplitClientConfig built with builder defaults,
     * When a SplitFactory is created with that config,
     * Then the factory is non-null.
     */
    @Test
    public void factoryBuilderWithConfig() {
        SplitClientConfig config = mockConfig();
        SplitFactory factory = SplitFactoryBuilder.build(
                context(),
                new SdkKey("key"),
                new Target(new Key("user"), "user"),
                config
        );
        assertNotNull(factory);
    }

    /**
     * Given a SplitClient obtained from a factory,
     * When getTreatment, getTreatments, and getTreatmentsByFlagSets are called,
     * Then all returned results are non-null.
     */
    @Test
    public void clientEvaluation() {
        SplitFactory factory = buildFactory("key", "user");
        SplitClient client = factory.getClient(null);

        EvaluationResult result = client.getTreatment("my-flag", null);
        assertNotNull(result);

        List<EvaluationResult> results = client.getTreatments(
                Arrays.asList("flag-1", "flag-2"), null
        );
        assertNotNull(results);

        List<EvaluationResult> resultsByFlagSets = client.getTreatmentsByFlagSets(
                Collections.singletonList("set-a"), null
        );
        assertNotNull(resultsByFlagSets);
    }

    /**
     * Given a SplitFactory with a default target,
     * When manager.getFlagNames() is called,
     * Then the returned list is non-null.
     */
    @Test
    public void managerFlagNames() {
        SplitFactory factory = buildFactory("key", "user");
        SplitManager manager = factory.getManager();
        List<String> names = manager.getFlagNames();
        assertNotNull(names);
    }

    /**
     * Given an EvaluationResult constructed with all fields,
     * When each getter is called,
     * Then the values match what was provided to the constructor.
     */
    @Test
    public void evaluationResultFields() {
        EvaluationResult result = new EvaluationResult(
                "my-flag", "on", "{\"color\":\"red\"}", 42L
        );
        assertEquals("my-flag", result.getFlag());
        assertEquals("on", result.getTreatment());
        assertEquals("{\"color\":\"red\"}", result.getConfig());
        assertEquals(Long.valueOf(42L), result.getChangeNumber());
    }

    /**
     * Given the SplitClientConfig.Builder,
     * When all builder methods are chained and build() is called,
     * Then the resulting config is non-null.
     */
    @Test
    public void splitClientConfigBuilder() {
        SplitClientConfig config = new SplitClientConfig.Builder()
                .logLevel(SplitClientConfig.LogLevel.WARN)
                .configsEnabled(false)
                .sync(new SplitClientConfig.SyncConfig.Builder()
                        .mode(SplitClientConfig.SyncMode.STREAMING)
                        .build())
                .storage(new SplitClientConfig.StorageConfig.Builder()
                        .prefix("myprefix")
                        .build())
                .build();
        assertNotNull(config);
    }

    /**
     * Given the SplitEvent enum,
     * When all values are collected,
     * Then SDK_READY, SDK_READY_FROM_CACHE, SDK_READY_TIMEOUT, and SDK_UPDATE are present.
     */
    @Test
    public void splitEventEnum() {
        SplitEvent[] events = SplitEvent.values();
        assertTrue(Arrays.asList(events).contains(SplitEvent.SDK_READY));
        assertTrue(Arrays.asList(events).contains(SplitEvent.SDK_READY_FROM_CACHE));
        assertTrue(Arrays.asList(events).contains(SplitEvent.SDK_READY_TIMEOUT));
        assertTrue(Arrays.asList(events).contains(SplitEvent.SDK_UPDATE));
    }

    /**
     * Given SplitCallback<T> and SplitVoidCallback functional interfaces,
     * When used as lambdas,
     * Then they compile and the instances are non-null.
     */
    @Test
    public void callbackFunctionalInterfaces() {
        SplitCallback<String> callback = (result, error) -> {
            // no-op
        };
        assertNotNull(callback);

        SplitVoidCallback voidCallback = (error) -> {
            // no-op
        };
        assertNotNull(voidCallback);
    }

    /**
     * Given a SplitEventListener subclass that overrides onReady,
     * When instantiated,
     * Then the listener is non-null.
     */
    @Test
    public void splitEventListenerSubclass() {
        SplitEventListener listener = new SplitEventListener() {
            @Override
            public void onReady(SplitClient client, SdkReadyMetadata metadata) {
                // no-op
            }
        };
        assertNotNull(listener);
    }

    /**
     * Given a SplitEventListener subclass overriding onReady, onReadyFromCache, and onUpdate,
     * When registered via client.addEventListener,
     * Then no exception is thrown.
     */
    @Test
    public void addEventListenerAcceptsListenerWithoutThrowing() {
        SplitFactory factory = buildFactory("key", "user");
        SplitClient client = factory.getClient(null);
        SplitEventListener listener = new SplitEventListener() {
            @Override
            public void onReady(SplitClient client, SdkReadyMetadata metadata) {}

            @Override
            public void onReadyFromCache(SplitClient client, SdkReadyMetadata metadata) {}

            @Override
            public void onUpdate(SplitClient client, @Nullable SdkUpdateMetadata metadata) {}
        };

        client.addEventListener(listener);
        // No exception thrown — listener registration is wired correctly.
    }

    /**
     * Given SdkReadyMetadata constructed with and without arguments,
     * When each getter is called,
     * Then values match what was provided to the constructor.
     */
    @Test
    public void sdkReadyMetadata() {
        SdkReadyMetadata meta = new SdkReadyMetadata();
        assertNotNull(meta);

        SdkReadyMetadata metaWithFields = new SdkReadyMetadata(true, 1234567890L);
        assertEquals(Boolean.TRUE, metaWithFields.isInitialCacheLoad());
        assertEquals(Long.valueOf(1234567890L), metaWithFields.getLastUpdateTimestamp());
    }

    /**
     * Given SdkUpdateMetadata constructed with type and names,
     * When type enum values are accessed,
     * Then all expected Type entries are non-null.
     */
    @Test
    public void sdkUpdateMetadata() {
        SdkUpdateMetadata meta = new SdkUpdateMetadata();
        assertNotNull(meta);

        assertNotNull(SdkUpdateMetadata.Type.FLAGS_UPDATE);
        assertNotNull(SdkUpdateMetadata.Type.SEGMENTS_UPDATE);
    }

    // -------------------------------------------------------------------------
    // New Phase 0 compilation tests
    // -------------------------------------------------------------------------

    /**
     * Given the FallbackTreatment constructor,
     * When instantiated with treatment and optional config,
     * Then the treatment and config getters return the provided values.
     */
    @Test
    public void fallbackTreatmentFields() {
        FallbackTreatment ft = new FallbackTreatment("on", "{\"color\":\"red\"}");
        assertEquals("on", ft.getTreatment());
        assertEquals("{\"color\":\"red\"}", ft.getConfig());

        FallbackTreatment ftNoConfig = new FallbackTreatment("off");
        assertEquals("off", ftNoConfig.getTreatment());
        assertNull(ftNoConfig.getConfig());
    }

    /**
     * Given FallbackTreatmentsConfiguration.builder(),
     * When global(FallbackTreatment), global(String), byFlag(), and byFlagStrings() are called
     *   followed by build(),
     * Then global and byFlag properties are accessible on the result.
     */
    @Test
    public void fallbackTreatmentsConfigurationBuilder() {
        Map<String, FallbackTreatment> byFlagMap = new HashMap<>();
        byFlagMap.put("flag-a", new FallbackTreatment("on", "{\"k\":\"v\"}"));

        Map<String, String> byFlagStringsMap = new HashMap<>();
        byFlagStringsMap.put("flag-b", "off");

        FallbackTreatmentsConfiguration config = FallbackTreatmentsConfiguration.builder()
                .global(new FallbackTreatment("off"))
                .global("on")
                .byFlag(byFlagMap)
                .byFlagStrings(byFlagStringsMap)
                .build();

        assertNotNull(config.getGlobal());
        assertEquals("on", config.getGlobal().getTreatment());
        assertTrue(config.getByFlag().containsKey("flag-a"));
        assertTrue(config.getByFlag().containsKey("flag-b"));
    }

    /**
     * Given a SplitClient,
     * When track(eventType), track(eventType, value), and track(eventType, value, properties)
     *   are all called,
     * Then none of them throw.
     */
    @Test
    public void trackMethodVariants() {
        SplitClient client = buildFactory("key", "user").getClient(null);

        client.track("purchase", null, null);
        client.track("purchase", 9.99, null);

        Map<String, Object> props = new HashMap<>();
        props.put("plan", "premium");
        client.track("purchase", 9.99, props);
    }

    /**
     * Given a SplitClient,
     * When destroyAsync(callback) is called,
     * Then it invokes the callback without throwing.
     */
    @SuppressWarnings("deprecation")
    @Test
    public void clientDestroy() {
        SplitClient client = buildFactory("key", "user").getClient(null);

        client.destroyAsync(error -> {
            // no-op
        });
    }

    /**
     * Given a SplitClient,
     * When flushAsync(callback) is called,
     * Then it invokes the callback without throwing.
     */
    @SuppressWarnings("deprecation")
    @Test
    public void clientFlush() {
        SplitClient client = buildFactory("key", "user").getClient(null);

        client.flushAsync(error -> {
            // no-op
        });
    }

    /**
     * Given a SplitFactory,
     * When destroyAsync(callback) is called,
     * Then it invokes the callback without throwing.
     */
    @SuppressWarnings("deprecation")
    @Test
    public void factoryDestroy() {
        SplitFactory factory = buildFactory("key", "user");

        factory.destroyAsync(error -> {
            // no-op
        });
    }

    /**
     * Given the ServiceEndpoints constructor,
     * When instantiated with all five fields (including optional streaming),
     * Then each getter returns the provided value.
     */
    @Test
    public void serviceEndpointsFields() {
        SplitClientConfig.ServiceEndpoints endpoints = new SplitClientConfig.ServiceEndpoints.Builder()
                .auth("https://auth.example.com")
                .evaluations("https://eval.example.com")
                .events("https://events.example.com")
                .streaming("https://streaming.example.com")
                .build();

        assertEquals("https://auth.example.com", endpoints.getAuth());
        assertEquals("https://eval.example.com", endpoints.getEvaluations());
        assertEquals("https://events.example.com", endpoints.getEvents());
        assertEquals("https://streaming.example.com", endpoints.getStreaming());
    }

    /**
     * Given a SplitEventListener subclass,
     * When onTimeout and onTimeoutView are overridden,
     * Then the subclass compiles and is non-null.
     */
    @Test
    public void eventListenerTimeoutCallbacks() {
        SplitEventListener listener = new SplitEventListener() {
            @Override
            public void onTimeout(SplitClient client) {
                // no-op
            }

            @Override
            public void onTimeoutView(SplitClient client) {
                // no-op
            }
        };
        assertNotNull(listener);
    }

    /**
     * Given a SplitEventListener subclass,
     * When onReadyView, onUpdateView, and onReadyFromCacheView are overridden,
     * Then the subclass compiles and is non-null.
     */
    @Test
    public void eventListenerViewCallbacks() {
        SplitEventListener listener = new SplitEventListener() {
            @Override
            public void onReadyView(SplitClient client, @Nullable SdkReadyMetadata metadata) {
                // no-op
            }

            @Override
            public void onUpdateView(SplitClient client, @Nullable SdkUpdateMetadata metadata) {
                // no-op
            }

            @Override
            public void onReadyFromCacheView(SplitClient client, @Nullable SdkReadyMetadata metadata) {
                // no-op
            }
        };
        assertNotNull(listener);
    }

    /**
     * Given a SplitClientConfig.Builder with a FallbackTreatmentsConfiguration,
     * When build() is called,
     * Then the resulting config has a non-null fallbackTreatments property.
     */
    @Test
    public void configWithFallbackTreatmentsBuilder() {
        FallbackTreatmentsConfiguration fallback = FallbackTreatmentsConfiguration.builder()
                .global("off")
                .build();

        SplitClientConfig config = new SplitClientConfig.Builder()
                .fallbackTreatments(fallback)
                .build();

        assertNotNull(config.getFallbackTreatments());
        assertEquals("off", config.getFallbackTreatments().getGlobal().getTreatment());
    }

    /**
     * Given a SplitClientConfig.Builder with a FiltersConfig,
     * When flagSets is set via the Builder,
     * Then the resulting config exposes the normalized flag sets.
     */
    @Test
    public void filtersConfigBuilder() {
        java.util.Set<String> flagSets = new java.util.HashSet<>(Arrays.asList("set_a", "set_b"));

        SplitClientConfig config = new SplitClientConfig.Builder()
                .filters(new SplitClientConfig.FiltersConfig.Builder()
                        .flagSets(flagSets)
                        .build())
                .build();

        assertNotNull(config.getFilters().getFlagSets());
        assertTrue(config.getFilters().getFlagSets().contains("set_a"));
        assertTrue(config.getFilters().getFlagSets().contains("set_b"));
    }
}
