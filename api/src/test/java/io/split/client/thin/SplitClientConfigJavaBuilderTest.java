package io.split.client.thin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import org.junit.Test;

public class SplitClientConfigJavaBuilderTest {

    @Test
    public void builderDefaultsAreAppliedToAllProperties() {
        SplitClientConfig config = new SplitClientConfig.Builder().build();

        assertNull(config.getFallbackTreatments());
        assertEquals(SplitClientConfig.LogLevel.NONE, config.getLogLevel());
        assertEquals(SplitClientConfig.ImpressionsMode.DEFAULT, config.getImpressionsMode());
        assertFalse(config.getDynamicConfig());
        assertEquals(SplitClientConfig.SyncMode.STREAMING, config.getSync().getMode());
        assertEquals(3600, config.getSync().getEvaluationRefreshRate());
        assertEquals(1800, config.getSync().getPushRate());
        assertNull(config.getSync().getServiceEndpoints());
        assertNull(config.getStorage().getPrefix());
        assertEquals(-1, config.getStorage().getTimeout());
    }

    @Test
    public void builderCanSetLogLevel() {
        SplitClientConfig config = new SplitClientConfig.Builder()
                .logLevel(SplitClientConfig.LogLevel.DEBUG)
                .build();

        assertEquals(SplitClientConfig.LogLevel.DEBUG, config.getLogLevel());
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
                new SplitClientConfig.ServiceEndpoints(
                        "https://auth.example.com",
                        "https://evaluations.example.com",
                        "https://events.example.com",
                        "https://telemetry.example.com",
                        "https://streaming.example.com"
                );

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
    public void builderCanSetStorageTimeout() {
        SplitClientConfig config = new SplitClientConfig.Builder()
                .storage(new SplitClientConfig.StorageConfig.Builder()
                        .timeout(30)
                        .build())
                .build();

        assertEquals(30, config.getStorage().getTimeout());
    }

    @Test
    public void builderFallsBackInvalidValuesToDefaults() {
        SplitClientConfig config = new SplitClientConfig.Builder()
                .sync(new SplitClientConfig.SyncConfig.Builder()
                        .evaluationRefreshRate(1)
                        .pushRate(1)
                        .build())
                .storage(new SplitClientConfig.StorageConfig.Builder()
                        .prefix("!!!invalid!!!")
                        .timeout(-2)
                        .build())
                .build();

        assertEquals(3600, config.getSync().getEvaluationRefreshRate());
        assertEquals(1800, config.getSync().getPushRate());
        assertNull(config.getStorage().getPrefix());
        assertEquals(-1, config.getStorage().getTimeout());
    }

    @Test
    public void builderAcceptsEvaluationRefreshRateAtMinimumBoundary() {
        SplitClientConfig config = new SplitClientConfig.Builder()
                .sync(new SplitClientConfig.SyncConfig.Builder()
                        .evaluationRefreshRate(60)
                        .build())
                .build();

        assertEquals(60, config.getSync().getEvaluationRefreshRate());
    }

    @Test
    public void builderAcceptsPushRateAtMinimumBoundary() {
        SplitClientConfig config = new SplitClientConfig.Builder()
                .sync(new SplitClientConfig.SyncConfig.Builder()
                        .pushRate(30)
                        .build())
                .build();

        assertEquals(30, config.getSync().getPushRate());
    }

    @Test
    public void builderAcceptsStorageTimeoutOfZero() {
        SplitClientConfig config = new SplitClientConfig.Builder()
                .storage(new SplitClientConfig.StorageConfig.Builder()
                        .timeout(0)
                        .build())
                .build();

        assertEquals(0, config.getStorage().getTimeout());
    }

    @Test
    public void builderAcceptsPrefixAtMaxLengthBoundary() {
        String prefix = "a".repeat(80);
        SplitClientConfig config = new SplitClientConfig.Builder()
                .storage(new SplitClientConfig.StorageConfig.Builder()
                        .prefix(prefix)
                        .build())
                .build();

        assertEquals(prefix, config.getStorage().getPrefix());
    }

    @Test
    public void builderFallsBackPrefixWhenExceedingMaxLength() {
        SplitClientConfig config = new SplitClientConfig.Builder()
                .storage(new SplitClientConfig.StorageConfig.Builder()
                        .prefix("a".repeat(81))
                        .build())
                .build();

        assertNull(config.getStorage().getPrefix());
    }

    @Test
    public void builderFallsBackPrefixWhenEmptyString() {
        SplitClientConfig config = new SplitClientConfig.Builder()
                .storage(new SplitClientConfig.StorageConfig.Builder()
                        .prefix("")
                        .build())
                .build();

        assertNull(config.getStorage().getPrefix());
    }

    @Test
    public void builderCanSetSyncModeSingleSync() {
        SplitClientConfig config = new SplitClientConfig.Builder()
                .sync(new SplitClientConfig.SyncConfig.Builder()
                        .mode(SplitClientConfig.SyncMode.SINGLE_SYNC)
                        .build())
                .build();

        assertEquals(SplitClientConfig.SyncMode.SINGLE_SYNC, config.getSync().getMode());
    }

    @Test
    public void differentConfigsHaveDifferentHashCodes() {
        SplitClientConfig a = new SplitClientConfig.Builder()
                .logLevel(SplitClientConfig.LogLevel.DEBUG)
                .build();
        SplitClientConfig b = new SplitClientConfig.Builder()
                .logLevel(SplitClientConfig.LogLevel.VERBOSE)
                .build();

        assertNotEquals(a.hashCode(), b.hashCode());
    }
}
