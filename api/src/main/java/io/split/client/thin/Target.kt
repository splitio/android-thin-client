package io.split.client.thin

data class Target @JvmOverloads constructor(
    /** Key representing a single traffic type. */
    val key: Key,
    /** Optional target attributes sent for remote evaluation. */
    val attributes: Map<String, Any?> = emptyMap(),
    /** Traffic type to be used when tracking events. */
    val trafficType: String? = null,
)
