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
        assertFalse(config.getConfigsEnabled());
        assertEquals(SplitClientConfig.SyncMode.STREAMING, config.getSync().getMode());
        assertEquals(3600, config.getSync().getPollingRate());
        assertEquals(1800, config.getSync().getPushRate());
        assertNull(config.getSync().getServiceEndpoints());
        assertEquals(10, config.getSync().getReadyTimeout());
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
    public void builderCanEnableConfigsEnabled() {
        SplitClientConfig config = new SplitClientConfig.Builder()
                .configsEnabled(true)
                .build();

        assertTrue(config.getConfigsEnabled());
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
    public void builderCanSetPollingRate() {
        SplitClientConfig config = new SplitClientConfig.Builder()
                .sync(new SplitClientConfig.SyncConfig.Builder()
                        .pollingRate(300)
                        .build())
                .build();

        assertEquals(300, config.getSync().getPollingRate());
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
                new SplitClientConfig.ServiceEndpoints.Builder()
                        .auth("https://auth.example.com")
                        .evaluations("https://evaluations.example.com")
                        .events("https://events.example.com")
                        .streaming("https://streaming.example.com")
                        .build();

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
    public void builderCanSetSyncReadyTimeout() {
        SplitClientConfig config = new SplitClientConfig.Builder()
                .sync(new SplitClientConfig.SyncConfig.Builder()
                        .readyTimeout(30)
                        .build())
                .build();

        assertEquals(30, config.getSync().getReadyTimeout());
    }

    @Test
    public void builderClampsInvalidValuesToMinimum() {
        SplitClientConfig config = new SplitClientConfig.Builder()
                .sync(new SplitClientConfig.SyncConfig.Builder()
                        .pollingRate(0)
                        .pushRate(1)
                        .readyTimeout(-2)
                        .build())
                .storage(new SplitClientConfig.StorageConfig.Builder()
                        .prefix("!!!invalid!!!")
                        .build())
                .build();

        assertEquals(1, config.getSync().getPollingRate());
        assertEquals(30, config.getSync().getPushRate());
        assertNull(config.getStorage().getPrefix());
        assertEquals(10, config.getSync().getReadyTimeout());
    }

    @Test
    public void builderAcceptsPollingRateAtMinimumBoundary() {
        SplitClientConfig config = new SplitClientConfig.Builder()
                .sync(new SplitClientConfig.SyncConfig.Builder()
                        .pollingRate(1)
                        .build())
                .build();

        assertEquals(1, config.getSync().getPollingRate());
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
    public void builderFallsBackSyncReadyTimeoutWhenZero() {
        SplitClientConfig config = new SplitClientConfig.Builder()
                .sync(new SplitClientConfig.SyncConfig.Builder()
                        .readyTimeout(0)
                        .build())
                .build();

        assertEquals(10, config.getSync().getReadyTimeout());
    }

    @Test
    public void builderAcceptsSyncReadyTimeoutOfMinusOne() {
        SplitClientConfig config = new SplitClientConfig.Builder()
                .sync(new SplitClientConfig.SyncConfig.Builder()
                        .readyTimeout(-1)
                        .build())
                .build();

        assertEquals(-1, config.getSync().getReadyTimeout());
    }

    @Test
    public void builderAcceptsSyncReadyTimeoutAtMinimumBoundary() {
        SplitClientConfig config = new SplitClientConfig.Builder()
                .sync(new SplitClientConfig.SyncConfig.Builder()
                        .readyTimeout(1)
                        .build())
                .build();

        assertEquals(1, config.getSync().getReadyTimeout());
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
