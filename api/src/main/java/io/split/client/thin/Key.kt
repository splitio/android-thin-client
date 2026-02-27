package io.split.client.thin

/**
 * Key values used to identify a target for evaluation.
 */
data class Key @JvmOverloads constructor(
    /** Stable key used for matching/evaluation. */
    val matchingKey: String,
    /** Optional key used for rollout bucketing. */
    val bucketingKey: String? = null
)
