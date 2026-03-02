package io.split.client.thin

/**
 * Main thin-client API for evaluations, events, lifecycle and target switching.
 */
interface SplitClient {

    /**
     * Returns the evaluation result for a single flag.
     */
    fun getTreatment(
        flag: String,
        evaluationOptions: EvaluationOptions? = null,
    ): EvaluationResult

    /**
     * Returns evaluation results for the provided flag names.
     */
    fun getTreatments(
        flags: List<String>,
        evaluationOptions: EvaluationOptions? = null,
    ): List<EvaluationResult>

    /**
     * Returns evaluation results for flags in the provided flag sets.
     */
    fun getTreatmentsByFlagSets(
        flagSets: List<String>,
        evaluationOptions: EvaluationOptions? = null,
    ): List<EvaluationResult>

    /**
     * Switches the client target used for subsequent evaluations.
     */
    @JvmSynthetic
    suspend fun setTarget(target: Target)

    @Deprecated("Use suspend setTarget()", level = DeprecationLevel.ERROR)
    fun setTargetAsync(target: Target, callback: SplitVoidCallback)

    /**
     * Registers a listener for SDK lifecycle and update events.
     */
    fun addEventListener(listener: SplitEventListener)

    /**
     * Tracks an event for the current client context.
     */
    @JvmSynthetic
    suspend fun track(
        trafficType: String,
        eventType: String,
        value: Double? = null,
        properties: Map<String, Any?>? = null,
    )

    @Deprecated("Use suspend track()", level = DeprecationLevel.ERROR)
    fun trackAsync(
        trafficType: String,
        eventType: String,
        value: Double? = null,
        properties: Map<String, Any?>? = null,
        callback: SplitVoidCallback,
    )

    /**
     * Flushes pending data and destroys this client instance.
     */
    @JvmSynthetic
    suspend fun destroy()

    @Deprecated("Use suspend destroy()", level = DeprecationLevel.ERROR)
    fun destroyAsync(callback: SplitVoidCallback)

    /**
     * Flushes pending events/telemetry without destroying the client.
     */
    @JvmSynthetic
    suspend fun flush()

    @Deprecated("Use suspend flush()", level = DeprecationLevel.ERROR)
    fun flushAsync(callback: SplitVoidCallback)
}
