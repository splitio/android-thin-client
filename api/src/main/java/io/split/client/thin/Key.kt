package io.split.client.thin

data class Key @JvmOverloads constructor(
    val matchingKey: String,
    val bucketingKey: String? = null
)
