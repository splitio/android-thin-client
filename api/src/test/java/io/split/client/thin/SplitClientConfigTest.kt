package io.split.client.thin

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
    fun `default config has configsEnabled false`() {
        val config = defaultConfig()
        assertFalse(config.configsEnabled)
    }

    @Test
    fun `default sync config has syncMode STREAMING`() {
        val config = defaultConfig()
        assertEquals(SplitClientConfig.SyncMode.STREAMING, config.sync.mode)
    }

    @Test
    fun `default sync config has pollingRate 3600`() {
        val config = defaultConfig()
        assertEquals(3600, config.sync.pollingRate)
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
    fun `default sync config has readyTimeout ten`() {
        val config = defaultConfig()
        assertEquals(10, config.sync.readyTimeout)
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
    fun `DSL creates config with configsEnabled`() {
        val config = splitClientConfig {
            configsEnabled = true
        }
        assertTrue(config.configsEnabled)
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
                pollingRate = 120
                pushRate = 60
            }
        }
        assertEquals(SplitClientConfig.SyncMode.POLLING, config.sync.mode)
        assertEquals(120, config.sync.pollingRate)
        assertEquals(60, config.sync.pushRate)
    }

    @Test
    fun `DSL creates config with storage block`() {
        val config = splitClientConfig {
            storage {
                prefix = "test_prefix"
            }
            sync {
                readyTimeout = 30
            }
        }
        assertEquals("test_prefix", config.storage.prefix)
        assertEquals(30, config.sync.readyTimeout)
    }

    @Test
    fun `DSL creates config with serviceEndpoints in sync block`() {
        val config = splitClientConfig {
            sync {
                serviceEndpoints {
                    auth = "https://auth.example.com"
                    evaluations = "https://evaluations.example.com"
                    events = "https://events.example.com"
                }
            }
        }
        assertEquals(
            SplitClientConfig.ServiceEndpoints(
                auth = "https://auth.example.com",
                evaluations = "https://evaluations.example.com",
                events = "https://events.example.com",
            ),
            config.sync.serviceEndpoints
        )
    }

    @Test
    fun `builder and DSL produce equal configs for same inputs`() {
        val endpoints = SplitClientConfig.ServiceEndpoints(
            auth = "https://auth.example.com",
            evaluations = "https://evaluations.example.com",
            events = "https://events.example.com",
        )
        val fallbacks = FallbackTreatmentsConfiguration.builder()
            .byFlagStrings(mapOf("flag_a" to "on"))
            .build()

        val viaBuilder = SplitClientConfig.Builder()
            .logLevel(SplitClientConfig.LogLevel.INFO)
            .configsEnabled(true)
            .fallbackTreatments(fallbacks)
            .sync(
                SplitClientConfig.SyncConfig.Builder()
                    .mode(SplitClientConfig.SyncMode.POLLING)
                    .pollingRate(300)
                    .pushRate(60)
                    .readyTimeout(10)
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
            configsEnabled = true
            fallbackTreatments = fallbacks
            sync {
                mode = SplitClientConfig.SyncMode.POLLING
                pollingRate = 300
                pushRate = 60
                readyTimeout = 10
                serviceEndpoints {
                    auth = endpoints.auth
                    evaluations = endpoints.evaluations
                    events = endpoints.events
                }
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
    fun `builder clamps pollingRate to minimum when below minimum`() {
        val config = SplitClientConfig.Builder()
            .sync(SplitClientConfig.SyncConfig.Builder().pollingRate(0).build())
            .build()
        assertEquals(1, config.sync.pollingRate)
    }

    @Test
    fun `builder clamps pushRate to minimum when below minimum`() {
        val config = SplitClientConfig.Builder()
            .sync(SplitClientConfig.SyncConfig.Builder().pushRate(1).build())
            .build()
        assertEquals(30, config.sync.pushRate)
    }

    @Test
    fun `builder falls back storage prefix to null when invalid`() {
        val config = SplitClientConfig.Builder()
            .storage(SplitClientConfig.StorageConfig.Builder().prefix("!!!invalid!!!").build())
            .build()
        assertNull(config.storage.prefix)
    }

    @Test
    fun `builder falls back sync readyTimeout to default when below minimum`() {
        val config = SplitClientConfig.Builder()
            .sync(SplitClientConfig.SyncConfig.Builder().readyTimeout(-2).build())
            .build()
        assertEquals(10, config.sync.readyTimeout)
    }

    @Test
    fun `builder accepts sync readyTimeout of minus one to disable timeout`() {
        val config = SplitClientConfig.Builder()
            .sync(SplitClientConfig.SyncConfig.Builder().readyTimeout(-1).build())
            .build()
        assertEquals(-1, config.sync.readyTimeout)
    }

    @Test
    fun `builder accepts sync readyTimeout at minimum boundary`() {
        val config = SplitClientConfig.Builder()
            .sync(SplitClientConfig.SyncConfig.Builder().readyTimeout(1).build())
            .build()
        assertEquals(1, config.sync.readyTimeout)
    }

    @Test
    fun `DSL clamps pollingRate to minimum when below minimum`() {
        val config = splitClientConfig { sync { pollingRate = 0 } }
        assertEquals(1, config.sync.pollingRate)
    }

    @Test
    fun `DSL clamps pushRate to minimum when below minimum`() {
        val config = splitClientConfig { sync { pushRate = 1 } }
        assertEquals(30, config.sync.pushRate)
    }

    @Test
    fun `DSL falls back prefix to null when invalid`() {
        val config = splitClientConfig { storage { prefix = "!!!invalid!!!" } }
        assertNull(config.storage.prefix)
    }

    @Test
    fun `DSL falls back sync readyTimeout to default when below minimum`() {
        val config = splitClientConfig { sync { readyTimeout = -2 } }
        assertEquals(10, config.sync.readyTimeout)
    }

    @Test
    fun `DSL accepts sync readyTimeout of minus one to disable timeout`() {
        val config = splitClientConfig { sync { readyTimeout = -1 } }
        assertEquals(-1, config.sync.readyTimeout)
    }

    @Test
    fun `DSL accepts sync readyTimeout at minimum boundary`() {
        val config = splitClientConfig { sync { readyTimeout = 1 } }
        assertEquals(1, config.sync.readyTimeout)
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
    fun `builder accepts pollingRate at minimum boundary`() {
        val config = SplitClientConfig.Builder()
            .sync(SplitClientConfig.SyncConfig.Builder().pollingRate(1).build())
            .build()
        assertEquals(1, config.sync.pollingRate)
    }

    @Test
    fun `builder accepts pushRate at minimum boundary`() {
        val config = SplitClientConfig.Builder()
            .sync(SplitClientConfig.SyncConfig.Builder().pushRate(30).build())
            .build()
        assertEquals(30, config.sync.pushRate)
    }

    @Test
    fun `builder falls back sync readyTimeout to default when zero`() {
        val config = SplitClientConfig.Builder()
            .sync(SplitClientConfig.SyncConfig.Builder().readyTimeout(0).build())
            .build()
        assertEquals(10, config.sync.readyTimeout)
    }

    @Test
    fun `DSL accepts pollingRate at minimum boundary`() {
        val config = splitClientConfig { sync { pollingRate = 1 } }
        assertEquals(1, config.sync.pollingRate)
    }

    @Test
    fun `DSL accepts pushRate at minimum boundary`() {
        val config = splitClientConfig { sync { pushRate = 30 } }
        assertEquals(30, config.sync.pushRate)
    }

    @Test
    fun `DSL falls back sync readyTimeout to default when zero`() {
        val config = splitClientConfig { sync { readyTimeout = 0 } }
        assertEquals(10, config.sync.readyTimeout)
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

    // --- FiltersConfig tests ---

    @Test
    fun `default filters config has null flagSets`() {
        val config = defaultConfig()
        assertNull(config.filters.flagSets)
    }

    @Test
    fun `builder can set filters flagSets`() {
        val config = SplitClientConfig.Builder()
            .filters(SplitClientConfig.FiltersConfig.Builder().flagSets(setOf("set_a", "set_b")).build())
            .build()
        assertEquals(setOf("set_a", "set_b"), config.filters.flagSets)
    }

    @Test
    fun `DSL can set filters flagSets`() {
        val config = splitClientConfig {
            filters {
                flagSets = setOf("set_a", "set_b")
            }
        }
        assertEquals(setOf("set_a", "set_b"), config.filters.flagSets)
    }

    @Test
    fun `normalizeFilters lowercases entries and warns`() {
        val config = SplitClientConfig.Builder()
            .filters(SplitClientConfig.FiltersConfig.Builder().flagSets(setOf("Set_A")).build())
            .build()
        assertEquals(setOf("set_a"), config.filters.flagSets)
    }

    @Test
    fun `normalizeFilters trims whitespace and warns`() {
        val config = SplitClientConfig.Builder()
            .filters(SplitClientConfig.FiltersConfig.Builder().flagSets(setOf(" set_a ")).build())
            .build()
        assertEquals(setOf("set_a"), config.filters.flagSets)
    }

    @Test
    fun `normalizeFilters drops entries failing regex`() {
        val config = SplitClientConfig.Builder()
            .filters(SplitClientConfig.FiltersConfig.Builder().flagSets(setOf("!!!bad", "good_set")).build())
            .build()
        assertEquals(setOf("good_set"), config.filters.flagSets)
    }

    @Test
    fun `normalizeFilters deduplicates entries`() {
        val config = SplitClientConfig.Builder()
            .filters(SplitClientConfig.FiltersConfig.Builder().flagSets(setOf("set_a", "set_a")).build())
            .build()
        assertEquals(setOf("set_a"), config.filters.flagSets)
    }

    @Test
    fun `normalizeFilters stores null when all entries invalid`() {
        val config = SplitClientConfig.Builder()
            .filters(SplitClientConfig.FiltersConfig.Builder().flagSets(setOf("!!!bad")).build())
            .build()
        assertNull(config.filters.flagSets)
    }

    @Test
    fun `normalizeFilters sorts entries`() {
        val config = SplitClientConfig.Builder()
            .filters(SplitClientConfig.FiltersConfig.Builder().flagSets(setOf("zz_set", "aa_set")).build())
            .build()
        assertEquals(listOf("aa_set", "zz_set"), config.filters.flagSets?.toList())
    }

    @Test
    fun `normalizeFilters drops entries exceeding 50 chars`() {
        val longName = "a".repeat(51)
        val config = SplitClientConfig.Builder()
            .filters(SplitClientConfig.FiltersConfig.Builder().flagSets(setOf(longName, "good")).build())
            .build()
        assertEquals(setOf("good"), config.filters.flagSets)
    }

    // --- normalizeSync with explicit minPollingRate (covers release-build semantics in debug variant) ---

    @Test
    fun `normalizeSync clamps pollingRate below 60 to 60 when minPollingRate is 60`() {
        val sync = SplitClientConfig.SyncConfig.Builder().pollingRate(30).build()
        val result = SplitClientConfig.normalizeSync(sync, minPollingRate = 60)
        assertEquals(60, result.pollingRate)
    }

    @Test
    fun `normalizeSync accepts pollingRate of exactly 60 when minPollingRate is 60`() {
        val sync = SplitClientConfig.SyncConfig.Builder().pollingRate(60).build()
        val result = SplitClientConfig.normalizeSync(sync, minPollingRate = 60)
        assertEquals(60, result.pollingRate)
    }

    private fun defaultConfig(): SplitClientConfig = SplitClientConfig.Builder().build()
}
