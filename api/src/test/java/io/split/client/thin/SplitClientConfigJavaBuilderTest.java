package io.split.client.thin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.split.android.client.fallback.FallbackTreatmentsConfiguration;
import java.util.Collections;
import org.junit.Test;

public class SplitClientConfigJavaBuilderTest {

    @Test
    public void builderDefaultsAreAppliedToAllProperties() {
        SplitClientConfig config = new SplitClientConfig.Builder().build();

        assertNull(config.getFallbackTreatments());
        assertEquals(SplitClientConfig.LogLevel.NONE, config.getLogLevel());
        assertEquals(-1, config.getTimeout());
        assertEquals(SplitClientConfig.ImpressionsMode.DEFAULT, config.getImpressionsMode());
        assertFalse(config.getDynamicConfig());
        assertEquals(SplitClientConfig.SyncMode.STREAMING, config.getSync().getMode());
        assertEquals(3600, config.getSync().getEvaluationRefreshRate());
        assertEquals(1800, config.getSync().getPushRate());
        assertNull(config.getSync().getServiceEndpoints());
        assertNull(config.getStorage().getPrefix());
    }

    @Test
    public void builderCanSetLogLevel() {
        SplitClientConfig config = new SplitClientConfig.Builder()
                .logLevel(SplitClientConfig.LogLevel.DEBUG)
                .build();

        assertEquals(SplitClientConfig.LogLevel.DEBUG, config.getLogLevel());
    }

    @Test
    public void builderCanSetTimeout() {
        SplitClientConfig config = new SplitClientConfig.Builder()
                .timeout(30)
                .build();

        assertEquals(30, config.getTimeout());
    }

    @Test
    public void builderCanSetImpressionsMode() {
        SplitClientConfig config = new SplitClientConfig.Builder()
                .impressionsMode(SplitClientConfig.ImpressionsMode.NONE)
                .build();

        assertEquals(SplitClientConfig.ImpressionsMode.NONE, config.getImpressionsMode());
    }

    @Test
    public void builderCanEnableDynamicConfig() {
        SplitClientConfig config = new SplitClientConfig.Builder()
                .dynamicConfig(true)
                .build();

        assertTrue(config.getDynamicConfig());
    }

    @Test
    public void builderCanSetFallbackTreatments() {
        FallbackTreatmentsConfiguration fallbacks = FallbackTreatmentsConfiguration.builder()
                .byFlagStrings(Collections.singletonMap("my_flag", "off"))
                .build();

        SplitClientConfig config = new SplitClientConfig.Builder()
                .fallbackTreatments(fallbacks)
                .build();

        assertEquals(fallbacks, config.getFallbackTreatments());
    }

    @Test
    public void builderCanSetSyncMode() {
        SplitClientConfig config = new SplitClientConfig.Builder()
                .sync(new SplitClientConfig.SyncConfig.Builder()
                        .mode(SplitClientConfig.SyncMode.POLLING)
                        .build())
                .build();

        assertEquals(SplitClientConfig.SyncMode.POLLING, config.getSync().getMode());
    }

    @Test
    public void builderCanSetEvaluationRefreshRate() {
        SplitClientConfig config = new SplitClientConfig.Builder()
                .sync(new SplitClientConfig.SyncConfig.Builder()
                        .evaluationRefreshRate(300)
                        .build())
                .build();

        assertEquals(300, config.getSync().getEvaluationRefreshRate());
    }

    @Test
    public void builderCanSetPushRate() {
        SplitClientConfig config = new SplitClientConfig.Builder()
                .sync(new SplitClientConfig.SyncConfig.Builder()
                        .pushRate(120)
                        .build())
                .build();

        assertEquals(120, config.getSync().getPushRate());
    }

    @Test
    public void builderCanSetServiceEndpoints() {
        SplitClientConfig.ServiceEndpoints endpoints =
                new SplitClientConfig.ServiceEndpoints("https://api.example.com");

        SplitClientConfig config = new SplitClientConfig.Builder()
                .sync(new SplitClientConfig.SyncConfig.Builder()
                        .serviceEndpoints(endpoints)
                        .build())
                .build();

        assertEquals(endpoints, config.getSync().getServiceEndpoints());
    }

    @Test
    public void builderCanSetStoragePrefix() {
        SplitClientConfig config = new SplitClientConfig.Builder()
                .storage(new SplitClientConfig.StorageConfig.Builder()
                        .prefix("my_prefix")
                        .build())
                .build();

        assertEquals("my_prefix", config.getStorage().getPrefix());
    }

    @Test
    public void builderFallsBackInvalidValuesToDefaults() {
        SplitClientConfig config = new SplitClientConfig.Builder()
                .timeout(-2)
                .sync(new SplitClientConfig.SyncConfig.Builder()
                        .evaluationRefreshRate(1)
                        .pushRate(1)
                        .build())
                .storage(new SplitClientConfig.StorageConfig.Builder()
                        .prefix("!!!invalid!!!")
                        .build())
                .build();

        assertEquals(-1, config.getTimeout());
        assertEquals(3600, config.getSync().getEvaluationRefreshRate());
        assertEquals(1800, config.getSync().getPushRate());
        assertNull(config.getStorage().getPrefix());
    }
}
