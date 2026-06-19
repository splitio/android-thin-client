package io.split.client.thin.internal

import io.split.client.thin.EvaluationResult
import io.split.client.thin.SplitClient
import io.split.client.thin.SplitEventListener
import io.split.client.thin.SplitFactory
import io.split.client.thin.SplitManager
import io.split.client.thin.SplitVoidCallback
import io.split.client.thin.Target

internal object NoOpSplitFactory : SplitFactory {
    override fun getClient(target: Target?): SplitClient = NoOpSplitClient
    override fun getManager(): SplitManager = NoOpSplitManager
    override suspend fun destroy() {}

    @Deprecated("Use suspend destroy()", level = DeprecationLevel.ERROR)
    override fun destroyAsync(callback: SplitVoidCallback) {
        callback.onComplete(null)
    }
}

internal object NoOpSplitClient : SplitClient {
    private const val CONTROL = "control"

    override fun getTreatment(flag: String): EvaluationResult =
        EvaluationResult(flag, CONTROL)

    override fun getTreatments(flags: List<String>): List<EvaluationResult> =
        flags.map { EvaluationResult(it, CONTROL) }

    override fun getTreatmentsByFlagSets(flagSets: List<String>): List<EvaluationResult> =
        emptyList()

    override fun setTarget(target: Target) {}
    override fun addEventListener(listener: SplitEventListener) {}
    override fun track(eventType: String, value: Double?, properties: Map<String, Any?>?): Boolean = false
    override suspend fun destroy() {}
    override suspend fun flush() {}

    @Deprecated("Use suspend destroy()", level = DeprecationLevel.ERROR)
    override fun destroyAsync(callback: SplitVoidCallback) {
        callback.onComplete(null)
    }

    @Deprecated("Use suspend flush()", level = DeprecationLevel.ERROR)
    override fun flushAsync(callback: SplitVoidCallback) {
        callback.onComplete(null)
    }
}

internal object NoOpSplitManager : SplitManager {
    override val flagNames: List<String> = emptyList()
}
