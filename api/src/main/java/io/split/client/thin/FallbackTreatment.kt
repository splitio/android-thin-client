package io.split.client.thin

/**
 * Represents a fallback treatment with an optional config string.
 *
 * Used to configure what treatment is returned when the Remote Evaluator
 * returns "control" (e.g., due to network issues or unknown flags).
 */
data class FallbackTreatment @JvmOverloads constructor(
    val treatment: String,
    val config: String? = null,
)
