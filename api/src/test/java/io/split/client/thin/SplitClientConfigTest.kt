package io.split.client.thin

import io.split.android.client.fallback.FallbackTreatmentsConfiguration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitClientConfigTest {

    @Test
    fun `default config has null fallbackTreatments`() {
        val config = defaultConfig()
        assertNull(config.fallbackTreatments)
    }

    @Test
    fun `default config has logLevel NONE`() {
        val config = defaultConfig()
        assertEquals(SplitClientConfig.LogLevel.NONE, config.logLevel)
    }

    @Test
    fun `default config has timeout minus one`() {
        val config = defaultConfig()
        assertEquals(-1, config.timeout)
    }

    @Test
    fun `default config has impressionsMode DEFAULT`() {
        val config = defaultConfig()
        assertEquals(SplitClientConfig.ImpressionsMode.DEFAULT, config.impressionsMode)
    }

    @Test
    fun `default config has dynamicConfig false`() {
        val config = defaultConfig()
        assertFalse(config.dynamicConfig)
    }

    @Test
    fun `default sync config has syncMode STREAMING`() {
        val config = defaultConfig()
        assertEquals(SplitClientConfig.SyncMode.STREAMING, config.sync.mode)
    }

    @Test
    fun `default sync config has evaluationRefreshRate 3600`() {
        val config = defaultConfig()
        assertEquals(3600, config.sync.evaluationRefreshRate)
    }

    @Test
    fun `default sync config has pushRate 1800`() {
        val config = defaultConfig()
        assertEquals(1800, config.sync.pushRate)
    }

    @Test
    fun `default sync config has null serviceEndpoints`() {
        val config = defaultConfig()
        assertNull(config.sync.serviceEndpoints)
    }

    @Test
    fun `default storage config has null prefix`() {
        val config = defaultConfig()
        assertNull(config.storage.prefix)
    }

    @Test
    fun `DSL creates config with logLevel`() {
        val config = splitClientConfig {
            logLevel = SplitClientConfig.LogLevel.VERBOSE
        }
        assertEquals(SplitClientConfig.LogLevel.VERBOSE, config.logLevel)
    }

    @Test
    fun `DSL creates config with timeout`() {
        val config = splitClientConfig {
            timeout = 60
        }
        assertEquals(60, config.timeout)
    }

    @Test
    fun `DSL creates config with impressionsMode`() {
        val config = splitClientConfig {
            impressionsMode = SplitClientConfig.ImpressionsMode.NONE
        }
        assertEquals(SplitClientConfig.ImpressionsMode.NONE, config.impressionsMode)
    }

    @Test
    fun `DSL creates config with dynamicConfig`() {
        val config = splitClientConfig {
            dynamicConfig = true
        }
        assertTrue(config.dynamicConfig)
    }

    @Test
    fun `DSL creates config with fallbackTreatments helper`() {
        val config = splitClientConfig {
            fallbackTreatments {
                global("off")
                byFlagStrings(mapOf("my_flag" to "on"))
            }
        }

        val expected = FallbackTreatmentsConfiguration.builder()
            .global("off")
            .byFlagStrings(mapOf("my_flag" to "on"))
            .build()

        assertEquals(expected, config.fallbackTreatments)
    }

    @Test
    fun `DSL creates config with sync block`() {
        val config = splitClientConfig {
            sync {
                mode = SplitClientConfig.SyncMode.POLLING
                evaluationRefreshRate = 120
                pushRate = 60
            }
        }
        assertEquals(SplitClientConfig.SyncMode.POLLING, config.sync.mode)
        assertEquals(120, config.sync.evaluationRefreshRate)
        assertEquals(60, config.sync.pushRate)
    }

    @Test
    fun `DSL creates config with storage block`() {
        val config = splitClientConfig {
            storage {
                prefix = "test_prefix"
            }
        }
        assertEquals("test_prefix", config.storage.prefix)
    }

    @Test
    fun `DSL creates config with serviceEndpoints in sync block`() {
        val endpoints = SplitClientConfig.ServiceEndpoints("https://api.example.com")
        val config = splitClientConfig {
            sync {
                serviceEndpoints = endpoints
            }
        }
        assertEquals(endpoints, config.sync.serviceEndpoints)
    }

    @Test
    fun `builder and DSL produce equal configs for same inputs`() {
        val endpoints = SplitClientConfig.ServiceEndpoints("https://api.example.com")
        val fallbacks = FallbackTreatmentsConfiguration.builder()
            .byFlagStrings(mapOf("flag_a" to "on"))
            .build()

        val viaBuilder = SplitClientConfig.Builder()
            .logLevel(SplitClientConfig.LogLevel.INFO)
            .timeout(10)
            .impressionsMode(SplitClientConfig.ImpressionsMode.NONE)
            .dynamicConfig(true)
            .fallbackTreatments(fallbacks)
            .sync(
                SplitClientConfig.SyncConfig.Builder()
                    .mode(SplitClientConfig.SyncMode.POLLING)
                    .evaluationRefreshRate(300)
                    .pushRate(60)
                    .serviceEndpoints(endpoints)
                    .build()
            )
            .storage(
                SplitClientConfig.StorageConfig.Builder()
                    .prefix("parity_prefix")
                    .build()
            )
            .build()

        val viaDsl = splitClientConfig {
            logLevel = SplitClientConfig.LogLevel.INFO
            timeout = 10
            impressionsMode = SplitClientConfig.ImpressionsMode.NONE
            dynamicConfig = true
            fallbackTreatments = fallbacks
            sync {
                mode = SplitClientConfig.SyncMode.POLLING
                evaluationRefreshRate = 300
                pushRate = 60
                serviceEndpoints = endpoints
            }
            storage {
                prefix = "parity_prefix"
            }
        }

        assertEquals(viaBuilder, viaDsl)
    }

    @Test
    fun `fallbackTreatmentsConfiguration helper builds expected config`() {
        val built = fallbackTreatmentsConfiguration {
            global("off")
            byFlagStrings(mapOf("flag_a" to "on"))
        }

        val expected = FallbackTreatmentsConfiguration.builder()
            .global("off")
            .byFlagStrings(mapOf("flag_a" to "on"))
            .build()

        assertEquals(expected, built)
    }

    @Test
    fun `builder falls back evaluationRefreshRate to default when below minimum`() {
        val config = SplitClientConfig.Builder()
            .sync(SplitClientConfig.SyncConfig.Builder().evaluationRefreshRate(1).build())
            .build()
        assertEquals(3600, config.sync.evaluationRefreshRate)
    }

    @Test
    fun `builder falls back pushRate to default when below minimum`() {
        val config = SplitClientConfig.Builder()
            .sync(SplitClientConfig.SyncConfig.Builder().pushRate(1).build())
            .build()
        assertEquals(1800, config.sync.pushRate)
    }

    @Test
    fun `builder falls back storage prefix to null when invalid`() {
        val config = SplitClientConfig.Builder()
            .storage(SplitClientConfig.StorageConfig.Builder().prefix("!!!invalid!!!").build())
            .build()
        assertNull(config.storage.prefix)
    }

    @Test
    fun `DSL falls back evaluationRefreshRate to default when below minimum`() {
        val config = splitClientConfig { sync { evaluationRefreshRate = 1 } }
        assertEquals(3600, config.sync.evaluationRefreshRate)
    }

    @Test
    fun `DSL falls back pushRate to default when below minimum`() {
        val config = splitClientConfig { sync { pushRate = 1 } }
        assertEquals(1800, config.sync.pushRate)
    }

    @Test
    fun `DSL falls back prefix to null when invalid`() {
        val config = splitClientConfig { storage { prefix = "!!!invalid!!!" } }
        assertNull(config.storage.prefix)
    }

    @Test
    fun `builder falls back timeout to default when below minimum`() {
        val config = SplitClientConfig.Builder().timeout(-2).build()
        assertEquals(-1, config.timeout)
    }

    @Test
    fun `DSL falls back timeout to default when below minimum`() {
        val config = splitClientConfig { timeout = -2 }
        assertEquals(-1, config.timeout)
    }

    @Test
    fun `two default configs are equal`() {
        assertEquals(defaultConfig(), defaultConfig())
    }

    @Test
    fun `two default configs have same hashCode`() {
        assertEquals(defaultConfig().hashCode(), defaultConfig().hashCode())
    }

    @Test
    fun `toString contains relevant field info`() {
        val config = defaultConfig()
        assertNotNull(config.toString())
    }

    private fun defaultConfig(): SplitClientConfig = SplitClientConfig.Builder().build()
}
