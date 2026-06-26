package io.split.client.thin

/**
 * Typed metadata for SDK_READY and SDK_READY_FROM_CACHE events.
 *
 * Contains information about cache state when the SDK becomes ready.
 *
 * @param isInitialCacheLoad true if this is an initial cache load with no usable cache.
 * @param lastUpdateTimestamp last successful cache timestamp in milliseconds since epoch.
 */
data class SdkReadyMetadata @JvmOverloads constructor(
    /**
     * True when the SDK starts without usable cached data (for example, fresh install).
     */
    val isInitialCacheLoad: Boolean? = null,
    /**
     * Last successful cache timestamp in milliseconds since epoch.
     */
    val lastUpdateTimestamp: Long? = null,
)
