package io.split.client.thin.internal

import io.split.android.client.tracker.Tracker
import io.split.client.thin.EvaluationOptions
import io.split.client.thin.EvaluationResult
import io.split.client.thin.SplitClient
import io.split.client.thin.SplitEventListener
import io.split.client.thin.SplitVoidCallback
import io.split.client.thin.Target

internal class DefaultSplitClient(
    initialTarget: Target,
    private val tracker: Tracker
) : SplitClient {

    @Volatile
    private var target: Target = initialTarget

    override fun getTreatment(
        flag: String,
        evaluationOptions: EvaluationOptions?
    ): EvaluationResult {
        TODO("Not yet implemented")
    }

    override fun getTreatments(
        flags: List<String>,
        evaluationOptions: EvaluationOptions?
    ): List<EvaluationResult> {
        TODO("Not yet implemented")
    }

    override fun getTreatmentsByFlagSets(
        flagSets: List<String>,
        evaluationOptions: EvaluationOptions?
    ): List<EvaluationResult> {
        TODO("Not yet implemented")
    }

    override suspend fun setTarget(target: Target) {
        this.target = target
    }

    override fun setTargetAsync(
        target: Target,
        callback: SplitVoidCallback
    ) {
        TODO("Not yet implemented")
    }

    override fun addEventListener(listener: SplitEventListener) {
        TODO("Not yet implemented")
    }

    override fun track(
        trafficType: String,
        eventType: String,
        value: Double?,
        properties: Map<String, Any?>?
    ) {
        val javaProperties =
            runCatching { properties as? Map<String, Any> }.getOrDefault(emptyMap())
        val isSdkReady = true // TODO
        tracker.track(
            target.key.matchingKey,
            trafficType,
            eventType,
            value ?: 0.0,
            javaProperties,
            isSdkReady,
        )
    }

    override suspend fun destroy() {
        flush()
        tracker.enableTracking(false)
    }

    override fun destroyAsync(callback: SplitVoidCallback) {
        TODO("Not yet implemented")
    }

    override suspend fun flush() {
        // no-op for milestone 1
    }

    override fun flushAsync(callback: SplitVoidCallback) {
        TODO("Not yet implemented")
    }
}
