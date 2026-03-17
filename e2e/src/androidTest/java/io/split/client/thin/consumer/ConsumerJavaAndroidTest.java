package io.split.client.thin.consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.split.client.thin.SplitCallback;
import io.split.client.thin.SplitVoidCallback;
import io.split.client.thin.EvaluationResult;
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

    @Test
    public void valueTypes() {
        Key key = new Key("user-123");
        assertEquals("user-123", key.getMatchingKey());

        Key keyWithBucketing = new Key("user-123", "bucket-456");
        assertEquals("bucket-456", keyWithBucketing.getBucketingKey());

        Target target = new Target(key);
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

    @Test
    public void factoryBuilderAndClientAccess() {
        SdkKey sdkKey = new SdkKey("test-sdk-key");
        Target target = new Target(new Key("user-1"));

        SplitFactory factory = SplitFactoryBuilder.build(sdkKey, target);
        assertNotNull(factory);

        SplitClient client = factory.getClient(null);
        assertNotNull(client);

        SplitClient targetedClient = factory.getClient(target);
        assertNotNull(targetedClient);

        SplitManager manager = factory.getManager();
        assertNotNull(manager);
    }

    @Test
    public void factoryBuilderWithConfig() {
        SplitClientConfig config = new SplitClientConfig.Builder().build();
        SplitFactory factory = SplitFactoryBuilder.build(
                new SdkKey("key"),
                new Target(new Key("user")),
                config
        );
        assertNotNull(factory);
    }

    @Test
    public void clientEvaluation() {
        SplitFactory factory = SplitFactoryBuilder.build(
                new SdkKey("key"),
                new Target(new Key("user"))
        );
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

    @Test
    public void managerFlagNames() {
        SplitFactory factory = SplitFactoryBuilder.build(
                new SdkKey("key"),
                new Target(new Key("user"))
        );
        SplitManager manager = factory.getManager();
        List<String> names = manager.getFlagNames();
        assertNotNull(names);
    }

    @Test
    public void evaluationResultFields() {
        EvaluationResult result = new EvaluationResult(
                "my-flag", "on", "{\"color\":\"red\"}", "default rule", 42L
        );
        assertEquals("my-flag", result.getFlag());
        assertEquals("on", result.getTreatment());
        assertEquals("{\"color\":\"red\"}", result.getConfig());
        assertEquals("default rule", result.getLabel());
        assertEquals(Long.valueOf(42L), result.getChangeNumber());
    }

    @Test
    public void splitClientConfigBuilder() {
        SplitClientConfig config = new SplitClientConfig.Builder()
                .logLevel(SplitClientConfig.LogLevel.WARN)
                .impressionsMode(SplitClientConfig.ImpressionsMode.DEFAULT)
                .dynamicConfig(false)
                .sync(new SplitClientConfig.SyncConfig.Builder()
                        .mode(SplitClientConfig.SyncMode.STREAMING)
                        .build())
                .storage(new SplitClientConfig.StorageConfig.Builder()
                        .prefix("myprefix")
                        .build())
                .build();
        assertNotNull(config);
    }

    @Test
    public void splitEventEnum() {
        SplitEvent[] events = SplitEvent.values();
        assertTrue(Arrays.asList(events).contains(SplitEvent.SDK_READY));
        assertTrue(Arrays.asList(events).contains(SplitEvent.SDK_READY_FROM_CACHE));
        assertTrue(Arrays.asList(events).contains(SplitEvent.SDK_READY_TIMEOUT));
        assertTrue(Arrays.asList(events).contains(SplitEvent.SDK_UPDATE));
    }

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

    @Test
    public void sdkReadyMetadata() {
        SdkReadyMetadata meta = new SdkReadyMetadata();
        assertNotNull(meta);

        SdkReadyMetadata metaWithFields = new SdkReadyMetadata(true, 1234567890L);
        assertEquals(Boolean.TRUE, metaWithFields.isInitialCacheLoad());
        assertEquals(Long.valueOf(1234567890L), metaWithFields.getLastUpdateTimestamp());
    }

    @Test
    public void sdkUpdateMetadata() {
        SdkUpdateMetadata meta = new SdkUpdateMetadata();
        assertNotNull(meta);

        assertNotNull(SdkUpdateMetadata.Type.FLAGS_UPDATE);
        assertNotNull(SdkUpdateMetadata.Type.SEGMENTS_UPDATE);
    }
}
