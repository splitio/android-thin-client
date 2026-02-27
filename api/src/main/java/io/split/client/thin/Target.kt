package io.split.client.thin

data class Target @JvmOverloads constructor(
    /** Target key components used for matching and bucketing. */
    val key: Key,
    /** Optional target attributes sent for remote evaluation. */
    val attributes: Map<String, Any?> = emptyMap(),
    /** Optional traffic type used when tracking events. */
    val trafficType: String? = null,
)
