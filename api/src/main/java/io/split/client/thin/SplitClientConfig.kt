package io.split.client.thin

import io.split.android.client.fallback.FallbackTreatmentsConfiguration
import io.split.android.client.utils.logger.Logger
import io.split.android.client.utils.logger.SplitLogLevel

/**
 * Configuration options for creating a [SplitFactory].
 *
 * Example – Kotlin DSL:
 * ```kotlin
 * val config = splitClientConfig {
 *     logLevel = SplitClientConfig.LogLevel.DEBUG
 *     timeout  = 10
 *     sync {
 *         mode                 = SplitClientConfig.SyncMode.POLLING
 *         evaluationRefreshRate = 120
 *     }
 *     storage {
 *         prefix = "my_app"
 *     }
 * }
 * ```
 *
 * Example – Java builder:
 * ```java
 * SplitClientConfig config = new SplitClientConfig.Builder()
 *     .logLevel(SplitClientConfig.LogLevel.DEBUG)
 *     .timeout(10)
 *     .sync(new SplitClientConfig.SyncConfig.Builder()
 *         .mode(SplitClientConfig.SyncMode.POLLING)
 *         .evaluationRefreshRate(120)
 *         .build())
 *     .storage(new SplitClientConfig.StorageConfig.Builder()
 *         .prefix("my_app")
 *         .build())
 *     .build();
 * ```
 */
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
     * Seconds before [SplitEvent.sdkTimeout] is emitted.
     * `-1` means no timeout. Default: `-1`.
     */
    val timeout: Int,
    /** Impressions mode reported to the Remote Evaluator. Default: [ImpressionsMode.DEFAULT]. */
    val impressionsMode: ImpressionsMode,
    /**
     * Whether to request dynamic configs from the Remote Evaluator alongside evaluations.
     * Default: `false`.
     */
    val dynamicConfig: Boolean,
    /** Sync-related options (mode, rates, endpoints). */
    val sync: SyncConfig,
    /** Persistent storage options. */
    val storage: StorageConfig,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SplitClientConfig) return false

        return fallbackTreatments == other.fallbackTreatments &&
            logLevel == other.logLevel &&
            timeout == other.timeout &&
            impressionsMode == other.impressionsMode &&
            dynamicConfig == other.dynamicConfig &&
            sync == other.sync &&
            storage == other.storage
    }

    override fun hashCode(): Int {
        var result = fallbackTreatments?.hashCode() ?: 0
        result = 31 * result + logLevel.hashCode()
        result = 31 * result + timeout
        result = 31 * result + impressionsMode.hashCode()
        result = 31 * result + dynamicConfig.hashCode()
        result = 31 * result + sync.hashCode()
        result = 31 * result + storage.hashCode()
        return result
    }

    override fun toString(): String =
        "SplitClientConfig(" +
            "fallbackTreatments=$fallbackTreatments, " +
            "logLevel=$logLevel, " +
            "timeout=$timeout, " +
            "impressionsMode=$impressionsMode, " +
            "dynamicConfig=$dynamicConfig, " +
            "sync=$sync, " +
            "storage=$storage" +
            ")"

    companion object {
        private val PREFIX_REGEX = Regex("^[a-zA-Z0-9_]{1,80}$")
        private const val DEFAULT_TIMEOUT = -1
        private const val DEFAULT_EVALUATION_REFRESH_RATE = 3600
        private const val DEFAULT_PUSH_RATE = 1800

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
            timeout: Int,
            impressionsMode: ImpressionsMode,
            dynamicConfig: Boolean,
            sync: SyncConfig,
            storage: StorageConfig,
        ): SplitClientConfig {
            // Apply level before normalization so validation logs are visible.
            applyLogLevel(logLevel)
            return SplitClientConfig(
                fallbackTreatments = fallbackTreatments,
                logLevel = logLevel,
                timeout = normalizeTimeout(timeout),
                impressionsMode = impressionsMode,
                dynamicConfig = dynamicConfig,
                sync = normalizeSync(sync),
                storage = normalizeStorage(storage),
            )
        }

        internal fun normalizeTimeout(timeout: Int): Int {
            if (timeout < -1) {
                Logger.w(
                    "SplitClientConfig validation failed: timeout must be >= -1. " +
                        "Received: $timeout. Falling back to default: $DEFAULT_TIMEOUT"
                )
                return DEFAULT_TIMEOUT
            }
            return timeout
        }

        internal fun normalizeSync(sync: SyncConfig): SyncConfig {
            var evaluationRefreshRate = sync.evaluationRefreshRate
            var pushRate = sync.pushRate

            if (evaluationRefreshRate < 60) {
                Logger.w(
                    "SplitClientConfig validation failed: sync.evaluationRefreshRate must be >= 60. " +
                        "Received: ${sync.evaluationRefreshRate}. Falling back to default: $DEFAULT_EVALUATION_REFRESH_RATE"
                )
                evaluationRefreshRate = DEFAULT_EVALUATION_REFRESH_RATE
            }

            if (pushRate < 30) {
                Logger.w(
                    "SplitClientConfig validation failed: sync.pushRate must be >= 30. " +
                        "Received: ${sync.pushRate}. Falling back to default: $DEFAULT_PUSH_RATE"
                )
                pushRate = DEFAULT_PUSH_RATE
            }

            return if (evaluationRefreshRate == sync.evaluationRefreshRate && pushRate == sync.pushRate) {
                sync
            } else {
                SyncConfig(
                    mode = sync.mode,
                    evaluationRefreshRate = evaluationRefreshRate,
                    pushRate = pushRate,
                    serviceEndpoints = sync.serviceEndpoints,
                )
            }
        }

        internal fun normalizeStorage(storage: StorageConfig): StorageConfig {
            val prefix = storage.prefix ?: return storage
            if (!PREFIX_REGEX.matches(prefix)) {
                Logger.w(
                    "SplitClientConfig validation failed: storage.prefix must match ^[a-zA-Z0-9_]{1,80}$. " +
                        "Received: $prefix. Falling back to default: null"
                )
                return StorageConfig(prefix = null)
            }
            return storage
        }
    }

    enum class LogLevel { NONE, ERROR, WARN, INFO, DEBUG, VERBOSE }

    enum class SyncMode { STREAMING, POLLING, SINGLE_SYNC }

    enum class ImpressionsMode { DEFAULT, NONE }

    /**
     * Custom service endpoint URLs.
     *
     * @param url Base URL.
     */
    data class ServiceEndpoints(val url: String)

    /**
     * Synchronization-related configuration.
     *
     * @property mode          Sync mode. Default: [SyncMode.STREAMING].
     * @property evaluationRefreshRate Polling interval in seconds. Default: `3600`. Min value: `60`.
     * @property pushRate      POST interval in seconds for events and telemetry. Default: `1800`. Min value: `30`.
     * @property serviceEndpoints Custom endpoints. Default: `null` (use SDK defaults).
     */
    data class SyncConfig(
        val mode: SyncMode,
        val evaluationRefreshRate: Int,
        val pushRate: Int,
        val serviceEndpoints: ServiceEndpoints?,
    ) {
        class Builder {
            private var mode: SyncMode = SyncMode.STREAMING
            private var evaluationRefreshRate: Int = 3600
            private var pushRate: Int = 1800
            private var serviceEndpoints: ServiceEndpoints? = null

            fun mode(value: SyncMode) = apply { mode = value }
            fun evaluationRefreshRate(value: Int) = apply { evaluationRefreshRate = value }
            fun pushRate(value: Int) = apply { pushRate = value }
            fun serviceEndpoints(value: ServiceEndpoints) = apply { serviceEndpoints = value }

            fun build() = SyncConfig(
                mode = mode,
                evaluationRefreshRate = evaluationRefreshRate,
                pushRate = pushRate,
                serviceEndpoints = serviceEndpoints,
            )
        }
    }

    /**
     * Persistent-storage-related configuration.
     *
     * @property prefix Prefix appended to the persistent storage identifier (DB name, key prefix).
     *   Must conform to `^[a-zA-Z0-9_]{1,80}$`. Default: `null` (no prefix).
     */
    data class StorageConfig(
        val prefix: String?,
    ) {
        class Builder {
            private var prefix: String? = null

            fun prefix(value: String) = apply { prefix = value }

            fun build() = StorageConfig(prefix = prefix)
        }
    }

    class Builder {
        private var fallbackTreatments: FallbackTreatmentsConfiguration? = null
        private var logLevel: LogLevel = LogLevel.NONE
        private var timeout: Int = -1
        private var impressionsMode: ImpressionsMode = ImpressionsMode.DEFAULT
        private var dynamicConfig: Boolean = false
        private var sync: SyncConfig = SyncConfig.Builder().build()
        private var storage: StorageConfig = StorageConfig.Builder().build()

        fun fallbackTreatments(value: FallbackTreatmentsConfiguration) = apply { fallbackTreatments = value }
        fun logLevel(value: LogLevel) = apply { logLevel = value }
        fun timeout(value: Int) = apply { timeout = value }
        fun impressionsMode(value: ImpressionsMode) = apply { impressionsMode = value }
        fun dynamicConfig(value: Boolean) = apply { dynamicConfig = value }
        fun sync(value: SyncConfig) = apply { sync = value }
        fun storage(value: StorageConfig) = apply { storage = value }

        fun build(): SplitClientConfig =
            createNormalized(
                fallbackTreatments = fallbackTreatments,
                logLevel = logLevel,
                timeout = timeout,
                impressionsMode = impressionsMode,
                dynamicConfig = dynamicConfig,
                sync = sync,
                storage = storage,
            )
    }
}

/** DSL scope for [SplitClientConfig.SyncConfig]. */
class SyncConfigDsl {
    var mode: SplitClientConfig.SyncMode = SplitClientConfig.SyncMode.STREAMING
    var evaluationRefreshRate: Int = 3600
    var pushRate: Int = 1800
    var serviceEndpoints: SplitClientConfig.ServiceEndpoints? = null

    internal fun build() = SplitClientConfig.SyncConfig(
        mode = mode,
        evaluationRefreshRate = evaluationRefreshRate,
        pushRate = pushRate,
        serviceEndpoints = serviceEndpoints,
    )
}

/** DSL scope for [SplitClientConfig.StorageConfig]. */
class StorageConfigDsl {
    var prefix: String? = null

    internal fun build() = SplitClientConfig.StorageConfig(prefix = prefix)
}

/** DSL scope for [SplitClientConfig]. */
class SplitClientConfigDsl {
    var fallbackTreatments: FallbackTreatmentsConfiguration? = null
    var logLevel: SplitClientConfig.LogLevel = SplitClientConfig.LogLevel.NONE
    var timeout: Int = -1
    var impressionsMode: SplitClientConfig.ImpressionsMode = SplitClientConfig.ImpressionsMode.DEFAULT
    var dynamicConfig: Boolean = false

    private var syncDsl = SyncConfigDsl()
    private var storageDsl = StorageConfigDsl()

    fun sync(block: SyncConfigDsl.() -> Unit) {
        syncDsl = SyncConfigDsl().apply(block)
    }

    fun storage(block: StorageConfigDsl.() -> Unit) {
        storageDsl = StorageConfigDsl().apply(block)
    }

    internal fun build() = SplitClientConfig.createNormalized(
        fallbackTreatments = fallbackTreatments,
        logLevel = logLevel,
        timeout = timeout,
        impressionsMode = impressionsMode,
        dynamicConfig = dynamicConfig,
        sync = syncDsl.build(),
        storage = storageDsl.build(),
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
 *         evaluationRefreshRate = 120
 *     }
 * }
 * ```
 */
fun splitClientConfig(block: SplitClientConfigDsl.() -> Unit): SplitClientConfig =
    SplitClientConfigDsl().apply(block).build()
