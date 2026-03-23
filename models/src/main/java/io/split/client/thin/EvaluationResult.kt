package io.split.client.thin

/**
 * Result returned by an evaluation call.
 */
data class EvaluationResult(
    /** Evaluated flag name. */
    val flag: String,
    /** Treatment value. */
    val treatment: String,
    /** Configurations. **/
    val config: String? = null,
    /** Label associated with the evaluation. */
    val label: String? = null,
    /** Change number associated with this result. */
    val changeNumber: Long? = null,
)
