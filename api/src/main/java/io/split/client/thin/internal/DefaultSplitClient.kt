package io.split.client.thin.internal

import io.split.android.client.fallback.FallbackTreatmentsCalculator
import io.harness.events.EventsManager
import io.split.android.client.tracker.Tracker
import io.split.client.thin.EvaluationOptions
import io.split.client.thin.EvaluationResult
import io.split.client.thin.SplitClient
import io.split.client.thin.SplitEvent
import io.split.client.thin.SplitEventListener
import io.split.client.thin.SplitVoidCallback
import io.split.client.thin.Target
import io.split.client.thin.internal.evaluation.EvaluationRepository
import io.split.client.thin.internal.evaluation.StoredEvaluation
import io.split.client.thin.internal.evaluation.toEvaluationKey
import io.split.client.thin.internal.secure.EvaluationFilters
import io.split.client.thin.internal.sdkevents.SdkInternalEvent
import io.split.client.thin.internal.sdkevents.SplitEventListenerAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class DefaultSplitClient(
    initialTarget: Target,
    private val tracker: Tracker,
    private val evaluationRepository: EvaluationRepository,
    private val filters: EvaluationFilters?,
    private val fallbackCalculator: FallbackTreatmentsCalculator?,
    private val eventsManager: EventsManager<SplitEvent, SdkInternalEvent, Any?>,
    private val flushOperation: suspend () -> Unit = {},
    private val scope: CoroutineScope,
    private val asyncBridge: AsyncBridgeLike = AsyncBridge(),
) : SplitClient {

    @Volatile
    private var target: Target = initialTarget

    override fun getTreatment(
        flag: String,
        evaluationOptions: EvaluationOptions?
    ): EvaluationResult {
        val evalKey = target.toEvaluationKey()
        val stored = evaluationRepository.getTreatment(evalKey, flag)
        return resolveResult(flag, stored)
    }

    override fun getTreatments(
        flags: List<String>,
        evaluationOptions: EvaluationOptions?
    ): List<EvaluationResult> {
        val evalKey = target.toEvaluationKey()
        val results = evaluationRepository.getTreatments(evalKey, flags.toSet())
        return flags.map { flag -> resolveResult(flag, results[flag]) }
    }

    override fun getTreatmentsByFlagSets(
        flagSets: List<String>,
        evaluationOptions: EvaluationOptions?
    ): List<EvaluationResult> {
        val evalKey = target.toEvaluationKey()
        val results = evaluationRepository.getTreatmentsByFlagSets(evalKey, flagSets.toSet())
        return results.values.map { stored -> resolveResult(stored.result.flag, stored) }
    }

    override fun setTarget(target: Target) {
        this.target = target
        scope.launch {
            evaluationRepository.setTarget(target, filters)
        }
    }

    override fun addEventListener(listener: SplitEventListener) {
        SplitEventListenerAdapter(listener, this).registerAll(eventsManager)
    }

    override fun track(
        eventType: String,
        value: Double?,
        properties: Map<String, Any?>?
    ) {
        val javaProperties =
            runCatching { properties as? Map<String, Any> }.getOrDefault(emptyMap())
        val isSdkReady = eventsManager.eventAlreadyTriggered(SplitEvent.SDK_READY)
        tracker.track(
            target.key.matchingKey,
            target.trafficType,
            eventType,
            value ?: 0.0,
            javaProperties,
            isSdkReady,
        )
    }

    override suspend fun destroy() {
        flush()
        tracker.enableTracking(false)
        eventsManager.destroy()
    }

    override fun destroyAsync(callback: SplitVoidCallback) =
        asyncBridge.executeAsync(callback) { destroy() }

    override suspend fun flush() {
        flushOperation()
    }

    override fun flushAsync(callback: SplitVoidCallback) =
        asyncBridge.executeAsync(callback) { flush() }

    private fun resolveResult(flag: String, stored: StoredEvaluation?): EvaluationResult {
        if (stored != null && stored.result.treatment != CONTROL) {
            return stored.result
        }
        val fallback = fallbackCalculator?.resolve(flag)
        if (fallback != null && fallback.treatment != CONTROL) {
            return EvaluationResult(flag, fallback.treatment, fallback.config, fallback.label)
        }
        return stored?.result ?: EvaluationResult(flag, CONTROL)
    }

    companion object {
        private const val CONTROL = "control"
    }
}
