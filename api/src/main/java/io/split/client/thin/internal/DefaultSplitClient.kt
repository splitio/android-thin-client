package io.split.client.thin.internal

import io.split.android.client.fallback.FallbackTreatmentsCalculator
import io.harness.events.EventsManager
import io.split.android.client.tracker.Tracker
import io.split.android.client.utils.logger.Logger
import io.split.client.thin.EvaluationResult
import io.split.client.thin.SplitClient
import io.split.client.thin.SplitEvent
import io.split.client.thin.SplitEventListener
import io.split.client.thin.SplitVoidCallback
import io.split.client.thin.Target
import io.split.client.thin.internal.evaluation.EvaluationKey
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
    private val filters: EvaluationFilters,
    private val fallbackCalculator: FallbackTreatmentsCalculator?,
    private val eventsManager: EventsManager<SplitEvent, SdkInternalEvent, Any?>,
    private val flushOperation: suspend () -> Unit = {},
    private val scope: CoroutineScope,
    private val onTargetChanged: (EvaluationKey, Target) -> Unit = { _, _ -> },
    private val asyncBridge: AsyncBridgeLike = AsyncBridge(),
    private val inputValidator: InputValidator = DefaultInputValidator(),
    private val destroyOperation: (suspend () -> Unit)? = null,
    private val releaseResources: () -> Unit = {},
    private val onDestroyForgetTarget: (EvaluationKey) -> Unit = {},
) : SplitClient, InternalDestroyable {

    @Volatile
    private var target: Target = initialTarget

    @Volatile
    private var destroyed = false

    override fun getTreatment(flag: String): EvaluationResult {
        if (destroyed) return resolveResult(flag, null)
        if (!inputValidator.validateFlagName(flag)) return resolveResult(flag, null)
        val trimmedFlag = flag.trim()
        val evalKey = target.toEvaluationKey()
        val stored = evaluationRepository.getTreatment(evalKey, trimmedFlag)
        return resolveResult(trimmedFlag, stored)
    }

    override fun getTreatments(flags: List<String>): List<EvaluationResult> {
        if (destroyed) return flags.map { resolveResult(it, null) }
        val evalKey = target.toEvaluationKey()
        val validationResults = flags.associateWith { inputValidator.validateFlagName(it) }
        val trimmedValid = validationResults
            .filterValues { it }
            .keys
            .map { it.trim() }
            .toSet()
        val results = evaluationRepository.getTreatments(evalKey, trimmedValid)
        return flags.map { flag ->
            if (validationResults[flag] != true) return@map resolveResult(flag, null)
            resolveResult(flag.trim(), results[flag.trim()])
        }
    }

    override fun getTreatmentsByFlagSets(flagSets: List<String>): List<EvaluationResult> {
        if (destroyed) return emptyList()
        val configuredSets = filters?.sets
        val effectiveSets = if (configuredSets != null && configuredSets.isNotEmpty()) {
            val requestedSet = flagSets.toSet()
            for (set in requestedSet) {
                if (set !in configuredSets) {
                    Logger.w("Flag Set $set is not part of the configured Flag set list, ignoring")
                }
            }
            val intersection = requestedSet.intersect(configuredSets)
            if (intersection.isEmpty()) return emptyList()
            intersection
        } else {
            flagSets.toSet()
        }
        val evalKey = target.toEvaluationKey()
        val results = evaluationRepository.getTreatmentsByFlagSets(evalKey, effectiveSets)
        return results.values.map { stored -> resolveResult(stored.result.flag, stored) }
    }

    override fun setTarget(target: Target) {
        if (!inputValidator.validateKey(target.key)) return
        val oldEvalKey = this.target.toEvaluationKey()
        this.target = target
        val newEvalKey = target.toEvaluationKey()
        if (oldEvalKey != newEvalKey) {
            onTargetChanged(oldEvalKey, target)
        }
    }

    override fun addEventListener(listener: SplitEventListener) {
        SplitEventListenerAdapter(listener, this).registerAll(eventsManager)
    }

    override fun track(
        eventType: String,
        value: Double?,
        properties: Map<String, Any?>?
    ): Boolean {
        if (destroyed) return false
        @Suppress("UNCHECKED_CAST")
        val javaProperties = properties as? Map<String, Any>
        val isSdkReady = eventsManager.eventAlreadyTriggered(SplitEvent.SDK_READY)
        return tracker.track(
            target.key.matchingKey,
            target.trafficType,
            eventType,
            value ?: 0.0,
            javaProperties,
            isSdkReady,
        )
    }

    override suspend fun tearDownInternal() {
        destroyed = true
        onDestroyForgetTarget(target.toEvaluationKey())
        flush()
        tracker.enableTracking(false)
        eventsManager.destroy()
        releaseResources()
    }

    override suspend fun destroy() {
        destroyOperation?.invoke() ?: tearDownInternal()
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
            return EvaluationResult(flag, fallback.treatment, fallback.config)
        }
        return stored?.result ?: EvaluationResult(flag, CONTROL)
    }

    companion object {
        private const val CONTROL = "control"
    }
}
