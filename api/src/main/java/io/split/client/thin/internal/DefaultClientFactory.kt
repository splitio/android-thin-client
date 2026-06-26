package io.split.client.thin.internal

import io.harness.events.EventsManager
import io.harness.events.EventsManagers
import io.split.android.client.fallback.FallbackTreatmentsCalculator
import io.split.android.client.tracker.DefaultTracker
import io.split.client.thin.SplitClient
import io.split.client.thin.SplitEvent
import io.split.client.thin.Target
import io.split.client.thin.events.EventTracker
import io.split.client.thin.internal.auth.AuthProvider
import io.split.client.thin.internal.evaluation.EvaluationFetchCoordinator
import io.split.client.thin.internal.evaluation.EvaluationKey
import io.split.client.thin.internal.evaluation.EvaluationRepository
import io.split.client.thin.internal.evaluation.EvaluationWriteStorage
import io.split.client.thin.internal.observer.CompositeObserver
import io.split.client.thin.internal.sdkevents.EventManagerObserver
import io.split.client.thin.internal.sdkevents.SdkInternalEvent
import io.split.client.thin.internal.sdkevents.SplitEventDelivery
import io.split.client.thin.internal.sdkevents.ThinClientEventsConfig
import io.split.client.thin.internal.secure.EvaluationFilters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class DefaultClientFactory(
    private val compositeObserver: CompositeObserver,
    private val scope: CoroutineScope,
    private val evaluationRepository: EvaluationRepository,
    private val filters: EvaluationFilters,
    private val fallbackCalculator: FallbackTreatmentsCalculator?,
    private val onEventPush: DefaultTracker.OnEventPush = DefaultTracker.OnEventPush { },
    private val flushFn: suspend () -> Unit = {},
    private val onInitFetchComplete: (() -> Unit)? = null,
    private val inputValidator: InputValidator = DefaultInputValidator(),
    private val authProvider: AuthProvider? = null,
    private val fetchCoordinator: EvaluationFetchCoordinator? = null,
    private val evaluationStorage: EvaluationWriteStorage? = null,
    private val eventsManagerFactory: () -> EventsManager<SplitEvent, SdkInternalEvent, Any?> = {
        EventsManagers.create(ThinClientEventsConfig.create(), SplitEventDelivery(scope))
    },
    private val deregister: (suspend (io.split.client.thin.Key) -> Unit)? = null,
) : (Target) -> SplitClient {

    override fun invoke(target: Target): SplitClient {
        val registrationKey = target.key
        val eventTracker = EventTracker.create(onEventPush)
        val eventsManager = eventsManagerFactory()
        val eventManagerObserver = EventManagerObserver(eventsManager, target.key.matchingKey)
        compositeObserver.register(eventManagerObserver)
        val client = DefaultSplitClient(
            initialTarget = target,
            tracker = eventTracker.tracker,
            evaluationRepository = evaluationRepository,
            filters = filters,
            fallbackCalculator = fallbackCalculator,
            eventsManager = eventsManager,
            flushOperation = flushFn,
            scope = scope,
            onTargetChanged = { oldEvalKey, newTarget ->
                onTargetChanged(oldEvalKey, newTarget, eventManagerObserver) {
                    eventsManager.eventAlreadyTriggered(SplitEvent.SDK_READY)
                }
            },
            inputValidator = inputValidator,
            destroyOperation = deregister?.let { op -> { op(registrationKey) } },
            releaseResources = { compositeObserver.unregister(eventManagerObserver) },
            onDestroyForgetTarget = { evalKey -> forgetTargetFetch(evalKey) },
        )
        scope.launch {
            evaluationRepository.setTarget(target, filters, isInitialization = true)
            onInitFetchComplete?.invoke()
        }
        return client
    }

    private fun forgetTargetFetch(evalKey: EvaluationKey) {
        fetchCoordinator?.forget(evalKey)
    }

    private fun onTargetChanged(
        oldEvalKey: EvaluationKey,
        newTarget: Target,
        eventManagerObserver: EventManagerObserver,
        isReadyFired: () -> Boolean,
    ) {
        eventManagerObserver.matchingKey = newTarget.key.matchingKey
        val oldMatchingKey = oldEvalKey.key.matchingKey
        val newMatchingKey = newTarget.key.matchingKey
        scope.launch {
            if (oldMatchingKey != newMatchingKey) {
                val auth = authProvider
                if (auth != null) {
                    val isNew = auth.addTarget(newMatchingKey)
                    if (isNew) auth.invalidateAll()
                    auth.removeTarget(oldMatchingKey)
                }
            }
            forgetTargetFetch(oldEvalKey)
            evaluationStorage?.clear(oldEvalKey)
            val isInit = !isReadyFired()
            evaluationRepository.setTarget(newTarget, filters, isInitialization = isInit)
        }
    }
}
