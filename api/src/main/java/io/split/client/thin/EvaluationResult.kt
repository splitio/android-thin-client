package io.split.client.thin

/**
 * Result returned by an evaluation call.
 */
data class EvaluationResult(
    /** Evaluated flag name. */
    val flag: String,
    /** Returned treatment value. */
    val treatment: String,
    /** Configurations **/
    val config: String? = null,
    /** Optional backend label associated with the evaluation. */
    val label: String? = null,
    /** Optional change number associated with this result. */
    val changeNumber: Long? = null,
)
