package io.split.client.thin

import io.split.android.client.fallback.FallbackTreatmentsConfiguration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

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
    fun `default storage config has timeout minus one`() {
        val config = defaultConfig()
        assertEquals(-1, config.storage.timeout)
    }

    @Test
    fun `split client config has no public user constructor`() {
        val constructors = SplitClientConfig::class.java.declaredConstructors
        assertTrue(constructors.isNotEmpty())
        assertTrue(constructors.any { Modifier.isPrivate(it.modifiers) })
    val hasPublicUserConstructor = constructors.any { ctor ->
            Modifier.isPublic(ctor.modifiers) &&
                ctor.parameterTypes.none { it.name == "kotlin.jvm.internal.DefaultConstructorMarker" }
        }
        assertFalse(hasPublicUserConstructor)
    }

    @Test
    fun `DSL creates config with logLevel`() {
        val config = splitClientConfig {
            logLevel = SplitClientConfig.LogLevel.VERBOSE
        }
        assertEquals(SplitClientConfig.LogLevel.VERBOSE, config.logLevel)
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
                timeout = 30
            }
        }
        assertEquals("test_prefix", config.storage.prefix)
        assertEquals(30, config.storage.timeout)
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
                    .timeout(10)
                    .build()
            )
            .build()

        val viaDsl = splitClientConfig {
            logLevel = SplitClientConfig.LogLevel.INFO
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
                timeout = 10
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
    fun `builder falls back storage timeout to default when below minimum`() {
        val config = SplitClientConfig.Builder()
            .storage(SplitClientConfig.StorageConfig.Builder().timeout(-2).build())
            .build()
        assertEquals(-1, config.storage.timeout)
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
    fun `DSL falls back storage timeout to default when below minimum`() {
        val config = splitClientConfig { storage { timeout = -2 } }
        assertEquals(-1, config.storage.timeout)
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
        val str = config.toString()
        assertTrue(str.contains("logLevel"))
        assertTrue(str.contains("sync"))
        assertTrue(str.contains("storage"))
    }

    @Test
    fun `builder accepts evaluationRefreshRate at minimum boundary`() {
        val config = SplitClientConfig.Builder()
            .sync(SplitClientConfig.SyncConfig.Builder().evaluationRefreshRate(60).build())
            .build()
        assertEquals(60, config.sync.evaluationRefreshRate)
    }

    @Test
    fun `builder accepts pushRate at minimum boundary`() {
        val config = SplitClientConfig.Builder()
            .sync(SplitClientConfig.SyncConfig.Builder().pushRate(30).build())
            .build()
        assertEquals(30, config.sync.pushRate)
    }

    @Test
    fun `builder accepts storage timeout of zero`() {
        val config = SplitClientConfig.Builder()
            .storage(SplitClientConfig.StorageConfig.Builder().timeout(0).build())
            .build()
        assertEquals(0, config.storage.timeout)
    }

    @Test
    fun `DSL accepts evaluationRefreshRate at minimum boundary`() {
        val config = splitClientConfig { sync { evaluationRefreshRate = 60 } }
        assertEquals(60, config.sync.evaluationRefreshRate)
    }

    @Test
    fun `DSL accepts pushRate at minimum boundary`() {
        val config = splitClientConfig { sync { pushRate = 30 } }
        assertEquals(30, config.sync.pushRate)
    }

    @Test
    fun `DSL accepts storage timeout of zero`() {
        val config = splitClientConfig { storage { timeout = 0 } }
        assertEquals(0, config.storage.timeout)
    }

    @Test
    fun `builder accepts prefix at max length boundary`() {
        val prefix = "a".repeat(80)
        val config = SplitClientConfig.Builder()
            .storage(SplitClientConfig.StorageConfig.Builder().prefix(prefix).build())
            .build()
        assertEquals(prefix, config.storage.prefix)
    }

    @Test
    fun `builder falls back prefix when exceeding max length`() {
        val config = SplitClientConfig.Builder()
            .storage(SplitClientConfig.StorageConfig.Builder().prefix("a".repeat(81)).build())
            .build()
        assertNull(config.storage.prefix)
    }

    @Test
    fun `builder falls back prefix when empty string`() {
        val config = SplitClientConfig.Builder()
            .storage(SplitClientConfig.StorageConfig.Builder().prefix("").build())
            .build()
        assertNull(config.storage.prefix)
    }

    @Test
    fun `builder can set syncMode SINGLE_SYNC`() {
        val config = SplitClientConfig.Builder()
            .sync(SplitClientConfig.SyncConfig.Builder().mode(SplitClientConfig.SyncMode.SINGLE_SYNC).build())
            .build()
        assertEquals(SplitClientConfig.SyncMode.SINGLE_SYNC, config.sync.mode)
    }

    @Test
    fun `DSL can set syncMode SINGLE_SYNC`() {
        val config = splitClientConfig { sync { mode = SplitClientConfig.SyncMode.SINGLE_SYNC } }
        assertEquals(SplitClientConfig.SyncMode.SINGLE_SYNC, config.sync.mode)
    }

    @Test
    fun `different configs have different hashCodes`() {
        val a = SplitClientConfig.Builder().logLevel(SplitClientConfig.LogLevel.DEBUG).build()
        val b = SplitClientConfig.Builder().logLevel(SplitClientConfig.LogLevel.VERBOSE).build()
        assertNotEquals(a.hashCode(), b.hashCode())
    }

    private fun defaultConfig(): SplitClientConfig = SplitClientConfig.Builder().build()
}
