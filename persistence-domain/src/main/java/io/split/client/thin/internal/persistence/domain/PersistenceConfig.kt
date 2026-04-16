package io.split.client.thin.internal.persistence.domain

data class PersistenceConfig(
    val prefix: String? = null,
    val enabled: Boolean = true,
    val sdkKey: String? = null,
    val dynamicConfig: Boolean = false,
)
