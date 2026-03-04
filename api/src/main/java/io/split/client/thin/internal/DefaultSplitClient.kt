package io.split.client.thin.internal

import io.split.client.thin.EvaluationOptions
import io.split.client.thin.EvaluationResult
import io.split.client.thin.SplitClient
import io.split.client.thin.SplitEventListener
import io.split.client.thin.SplitVoidCallback
import io.split.client.thin.Target

class DefaultSplitClient: SplitClient {

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
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    override suspend fun destroy() {
        TODO("Not yet implemented")
    }

    override fun destroyAsync(callback: SplitVoidCallback) {
        TODO("Not yet implemented")
    }

    override suspend fun flush() {
        TODO("Not yet implemented")
    }

    override fun flushAsync(callback: SplitVoidCallback) {
        TODO("Not yet implemented")
    }
}
