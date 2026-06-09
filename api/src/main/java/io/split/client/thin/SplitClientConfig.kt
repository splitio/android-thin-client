package io.split.client.thin

import io.split.android.client.utils.logger.Logger
import io.split.android.client.utils.logger.SplitLogLevel

/**
 * Configuration options for creating a [SplitFactory].
 *
 * Example – Kotlin DSL:
 * ```kotlin
 * val config = splitClientConfig {
 *     logLevel = SplitClientConfig.LogLevel.DEBUG
 *     sync {
 *         mode                 = SplitClientConfig.SyncMode.POLLING
 *         pollingRate = 120
 *     }
 *     storage {
 *         prefix  = "my_app"
 *         timeout = 10
 *     }
 * }
 * ```
 *
 * Example – Java builder:
 * ```java
 * SplitClientConfig config = new SplitClientConfig.Builder()
 *     .logLevel(SplitClientConfig.LogLevel.DEBUG)
 *     .sync(new SplitClientConfig.SyncConfig.Builder()
 *         .mode(SplitClientConfig.SyncMode.POLLING)
 *         .pollingRate(120)
 *         .build())
 *     .storage(new SplitClientConfig.StorageConfig.Builder()
 *         .prefix("my_app")
 *         .timeout(10)
 *         .build())
 *     .build();
 * ```
 */
private val FLAG_SET_REGEX = Regex("^[a-z0-9][_a-z0-9]{0,49}$")

class SplitClientConfig private constructor(
    /**
     * Fallback treatments per flag name.
     *
     * Key: flag name. Value: treatment string.
     *
     * Default: `null` (no overrides — `control` is returned as-is).
     */
    val fallbackTreatments: FallbackTreatmentsConfiguration?,
    /** Logging level. Default: [LogLevel.NONE]. */
    val logLevel: LogLevel,
    /**
     * Whether to request dynamic configs from the Remote Evaluator alongside evaluations.
     * Default: `false`.
     */
    val configsEnabled: Boolean,
    /** Sync-related options (mode, rates, endpoints). */
    val sync: SyncConfig,
    /** Persistent storage options (prefix, timeout). */
    val storage: StorageConfig,
    /** Evaluation filter options (flag sets). */
    val filters: FiltersConfig,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SplitClientConfig) return false

        return fallbackTreatments == other.fallbackTreatments &&
            logLevel == other.logLevel &&
            configsEnabled == other.configsEnabled &&
            sync == other.sync &&
            storage == other.storage &&
            filters == other.filters
    }

    override fun hashCode(): Int {
        var result = fallbackTreatments?.hashCode() ?: 0
        result = 31 * result + logLevel.hashCode()
        result = 31 * result + configsEnabled.hashCode()
        result = 31 * result + sync.hashCode()
        result = 31 * result + storage.hashCode()
        result = 31 * result + filters.hashCode()
        return result
    }

    override fun toString(): String =
        "SplitClientConfig(" +
            "fallbackTreatments=$fallbackTreatments, " +
            "logLevel=$logLevel, " +
            "configsEnabled=$configsEnabled, " +
            "sync=$sync, " +
            "storage=$storage, " +
            "filters=$filters" +
            ")"

    companion object {
        private val PREFIX_REGEX = Regex("^[a-zA-Z0-9_]{1,80}$")
        private const val DEFAULT_READY_TIMEOUT = 10
        private const val DEFAULT_PUSH_RATE = 1800
        private val MIN_POLLING_RATE = BuildConfig.MIN_EVALUATION_REFRESH_RATE

        internal fun applyLogLevel(logLevel: LogLevel) {
            val loggerLevel = when (logLevel) {
                LogLevel.NONE -> SplitLogLevel.NONE
                LogLevel.ERROR -> SplitLogLevel.ERROR
                LogLevel.WARN -> SplitLogLevel.WARNING
                LogLevel.INFO -> SplitLogLevel.INFO
                LogLevel.DEBUG -> SplitLogLevel.DEBUG
                LogLevel.VERBOSE -> SplitLogLevel.VERBOSE
            }
            Logger.instance().setLevel(loggerLevel)
        }

        internal fun createNormalized(
            fallbackTreatments: FallbackTreatmentsConfiguration?,
            logLevel: LogLevel,
            configsEnabled: Boolean,
            sync: SyncConfig,
            storage: StorageConfig,
            filters: FiltersConfig = FiltersConfig(flagSets = null),
        ): SplitClientConfig {
            // Apply level before normalization so validation logs are visible.
            applyLogLevel(logLevel)
            return SplitClientConfig(
                fallbackTreatments = fallbackTreatments,
                logLevel = logLevel,
                configsEnabled = configsEnabled,
                sync = normalizeSync(sync),
                storage = normalizeStorage(storage),
                filters = normalizeFilters(filters),
            )
        }

        internal fun normalizeFilters(filters: FiltersConfig): FiltersConfig {
            val rawSets = filters.flagSets ?: return FiltersConfig(flagSets = null)
            val normalized = sortedSetOf<String>()
            for (raw in rawSets) {
                val trimmed = raw.trim()
                if (trimmed != raw) {
                    Logger.w("SplitClientConfig: flag set '$raw' was trimmed to '$trimmed'.")
                }
                val lowered = trimmed.lowercase()
                if (lowered != trimmed) {
                    Logger.w("SplitClientConfig: flag set '$trimmed' was lowercased to '$lowered'.")
                }
                if (!FLAG_SET_REGEX.matches(lowered)) {
                    Logger.w("SplitClientConfig: flag set '$lowered' does not match required pattern and will be ignored.")
                    continue
                }
                normalized.add(lowered)
            }
            return if (normalized.isEmpty()) FiltersConfig(flagSets = null) else FiltersConfig(flagSets = normalized)
        }

        internal fun normalizeSync(sync: SyncConfig, minPollingRate: Int = MIN_POLLING_RATE): SyncConfig {
            var pollingRate = sync.pollingRate
            var pushRate = sync.pushRate
            var readyTimeout = sync.readyTimeout

            if (pollingRate < minPollingRate) {
                Logger.w(
                    "SplitClientConfig validation failed: sync.pollingRate must be >= $MIN_POLLING_RATE. " +
                        "Received: ${sync.pollingRate}. Falling back to minimum: $MIN_POLLING_RATE"
                )
                pollingRate = minPollingRate
            }

            if (pushRate < 30) {
                Logger.w(
                    "SplitClientConfig validation failed: sync.pushRate must be >= 30. " +
                        "Received: ${sync.pushRate}. Falling back to minimum: 30"
                )
                pushRate = 30
            }

            if (readyTimeout != -1 && readyTimeout < 1) {
                Logger.w(
                    "SplitClientConfig validation failed: sync.readyTimeout must be >= 1 or -1 (disabled). " +
                        "Received: $readyTimeout. Falling back to default: $DEFAULT_READY_TIMEOUT"
                )
                readyTimeout = DEFAULT_READY_TIMEOUT
            }

            return if (pollingRate == sync.pollingRate && pushRate == sync.pushRate && readyTimeout == sync.readyTimeout) {
                sync
            } else {
                SyncConfig(
                    mode = sync.mode,
                    pollingRate = pollingRate,
                    pushRate = pushRate,
                    serviceEndpoints = sync.serviceEndpoints,
                    readyTimeout = readyTimeout,
                )
            }
        }

        internal fun normalizeStorage(storage: StorageConfig): StorageConfig {
            var prefix = storage.prefix

            if (prefix != null && !PREFIX_REGEX.matches(prefix)) {
                Logger.w(
                    "SplitClientConfig validation failed: storage.prefix must match ^[a-zA-Z0-9_]{1,80}$. " +
                        "Received: $prefix. Falling back to default: null"
                )
                prefix = null
            }

            return if (prefix == storage.prefix) {
                storage
            } else {
                StorageConfig(prefix = prefix)
            }
        }
    }

    enum class LogLevel { NONE, ERROR, WARN, INFO, DEBUG, VERBOSE }

    enum class SyncMode { STREAMING, POLLING, SINGLE_SYNC }

    /**
     * Custom service endpoint base URLs (scheme + host only; paths are appended automatically).
     * Any field left `null` falls back to the SDK default host.
     */
    data class ServiceEndpoints internal constructor(
        val auth: String? = null,
        val evaluations: String? = null,
        val events: String? = null,
        val streaming: String? = null,
    ) {
        class Builder {
            private var auth: String? = null
            private var evaluations: String? = null
            private var events: String? = null
            private var streaming: String? = null

            fun auth(value: String) = apply { auth = value }
            fun evaluations(value: String) = apply { evaluations = value }
            fun events(value: String) = apply { events = value }
            fun streaming(value: String) = apply { streaming = value }

            fun build() = ServiceEndpoints(auth, evaluations, events, streaming)
        }
    }

    /**
     * Synchronization-related configuration.
     *
     * @property mode          Sync mode. Default: [SyncMode.STREAMING].
     * @property pollingRate   Polling interval in seconds. Default: `3600`. Min value: `60`.
     * @property pushRate      POST interval in seconds for events and telemetry. Default: `1800`. Min value: `30`.
     * @property serviceEndpoints Custom endpoints. Default: `null` (use SDK defaults).
     */
    data class SyncConfig internal constructor(
        val mode: SyncMode,
        val pollingRate: Int,
        val pushRate: Int,
        val serviceEndpoints: ServiceEndpoints?,
        val readyTimeout: Int,
    ) {
        class Builder {
            private var mode: SyncMode = SyncMode.STREAMING
            private var pollingRate: Int = 3600
            private var pushRate: Int = 1800
            private var serviceEndpoints: ServiceEndpoints? = null
            private var readyTimeout: Int = 10

            fun mode(value: SyncMode) = apply { mode = value }
            fun pollingRate(value: Int) = apply { pollingRate = value }
            fun pushRate(value: Int) = apply { pushRate = value }
            fun serviceEndpoints(value: ServiceEndpoints) = apply { serviceEndpoints = value }
            fun readyTimeout(value: Int) = apply { readyTimeout = value }

            fun build() = SyncConfig(
                mode = mode,
                pollingRate = pollingRate,
                pushRate = pushRate,
                serviceEndpoints = serviceEndpoints,
                readyTimeout = readyTimeout,
            )
        }
    }

    /**
     * Persistent-storage-related configuration.
     *
     * @property prefix   Prefix appended to the persistent storage identifier (DB name, key prefix).
     *   Must conform to `^[a-zA-Z0-9_]{1,80}$`. Default: `null` (no prefix).
     */
    data class StorageConfig internal constructor(
        val prefix: String?,
    ) {
        class Builder {
            private var prefix: String? = null

            fun prefix(value: String) = apply { prefix = value }

            fun build() = StorageConfig(prefix = prefix)
        }
    }

    /**
     * Evaluation filter configuration.
     *
     * @property flagSets Set of flag set names to fetch. `null` means no filter (fetch all).
     */
    data class FiltersConfig internal constructor(
        val flagSets: Set<String>?,
    ) {
        class Builder {
            private var flagSets: Set<String>? = null

            fun flagSets(value: Set<String>) = apply { flagSets = value }

            fun build() = FiltersConfig(flagSets = flagSets)
        }
    }

    class Builder {
        private var fallbackTreatments: FallbackTreatmentsConfiguration? = null
        private var logLevel: LogLevel = LogLevel.NONE
        private var configsEnabled: Boolean = false
        private var sync: SyncConfig = SyncConfig.Builder().build()
        private var storage: StorageConfig = StorageConfig.Builder().build()
        private var filters: FiltersConfig = FiltersConfig.Builder().build()

        fun fallbackTreatments(value: FallbackTreatmentsConfiguration) = apply { fallbackTreatments = value }
        fun logLevel(value: LogLevel) = apply { logLevel = value }
        fun configsEnabled(value: Boolean) = apply { configsEnabled = value }
        fun sync(value: SyncConfig) = apply { sync = value }
        fun storage(value: StorageConfig) = apply { storage = value }
        fun filters(value: FiltersConfig) = apply { filters = value }

        fun build(): SplitClientConfig =
            createNormalized(
                fallbackTreatments = fallbackTreatments,
                logLevel = logLevel,
                configsEnabled = configsEnabled,
                sync = sync,
                storage = storage,
                filters = filters,
            )
    }
}

/** DSL scope for [SplitClientConfig.ServiceEndpoints]. */
class ServiceEndpointsDsl {
    var auth: String? = null
    var evaluations: String? = null
    var events: String? = null
    var streaming: String? = null

    internal fun build() = SplitClientConfig.ServiceEndpoints(
        auth = auth,
        evaluations = evaluations,
        events = events,
        streaming = streaming,
    )
}

/** DSL scope for [SplitClientConfig.SyncConfig]. */
class SyncConfigDsl {
    var mode: SplitClientConfig.SyncMode = SplitClientConfig.SyncMode.STREAMING
    var pollingRate: Int = 3600
    var pushRate: Int = 1800
    var readyTimeout: Int = 10
    private var _serviceEndpoints: SplitClientConfig.ServiceEndpoints? = null

    fun serviceEndpoints(block: ServiceEndpointsDsl.() -> Unit) {
        _serviceEndpoints = ServiceEndpointsDsl().apply(block).build()
    }

    internal fun build() = SplitClientConfig.SyncConfig(
        mode = mode,
        pollingRate = pollingRate,
        pushRate = pushRate,
        serviceEndpoints = _serviceEndpoints,
        readyTimeout = readyTimeout,
    )
}

/** DSL scope for [SplitClientConfig.StorageConfig]. */
class StorageConfigDsl {
    var prefix: String? = null

    internal fun build() = SplitClientConfig.StorageConfig(prefix = prefix)
}

/** DSL scope for [SplitClientConfig.FiltersConfig]. */
class FiltersConfigDsl {
    var flagSets: Set<String>? = null

    internal fun build() = SplitClientConfig.FiltersConfig(flagSets = flagSets)
}

/** DSL scope for [SplitClientConfig]. */
class SplitClientConfigDsl {
    var fallbackTreatments: FallbackTreatmentsConfiguration? = null
    var logLevel: SplitClientConfig.LogLevel = SplitClientConfig.LogLevel.NONE
    var configsEnabled: Boolean = false

    private var syncDsl = SyncConfigDsl()
    private var storageDsl = StorageConfigDsl()
    private var filtersDsl = FiltersConfigDsl()

    fun sync(block: SyncConfigDsl.() -> Unit) {
        syncDsl = SyncConfigDsl().apply(block)
    }

    fun storage(block: StorageConfigDsl.() -> Unit) {
        storageDsl = StorageConfigDsl().apply(block)
    }

    fun filters(block: FiltersConfigDsl.() -> Unit) {
        filtersDsl = FiltersConfigDsl().apply(block)
    }

    internal fun build() = SplitClientConfig.createNormalized(
        fallbackTreatments = fallbackTreatments,
        logLevel = logLevel,
        configsEnabled = configsEnabled,
        sync = syncDsl.build(),
        storage = storageDsl.build(),
        filters = filtersDsl.build(),
    )
}

/**
 * Kotlin helper for creating [FallbackTreatmentsConfiguration] in a DSL style.
 */
fun fallbackTreatmentsConfiguration(
    block: FallbackTreatmentsConfiguration.Builder.() -> Unit,
): FallbackTreatmentsConfiguration =
    FallbackTreatmentsConfiguration.builder().apply(block).build()

/**
 * Kotlin DSL extension for setting [SplitClientConfigDsl.fallbackTreatments].
 *
 * Example:
 * ```kotlin
 * splitClientConfig {
 *   fallbackTreatments {
 *     global("off")
 *     byFlagStrings(mapOf("my_flag" to "on"))
 *   }
 * }
 * ```
 */
fun SplitClientConfigDsl.fallbackTreatments(
    block: FallbackTreatmentsConfiguration.Builder.() -> Unit,
) {
    fallbackTreatments = fallbackTreatmentsConfiguration(block)
}

/**
 * Kotlin DSL entry point for building a [SplitClientConfig].
 *
 * ```kotlin
 * val config = splitClientConfig {
 *     logLevel = SplitClientConfig.LogLevel.DEBUG
 *     sync {
 *         pollingRate = 120
 *     }
 *     storage {
 *         timeout = 10
 *     }
 * }
 * ```
 */
fun splitClientConfig(block: SplitClientConfigDsl.() -> Unit): SplitClientConfig =
    SplitClientConfigDsl().apply(block).build()
