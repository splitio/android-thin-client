package io.split.client.thin.internal

import io.split.android.client.fallback.FallbackTreatmentsCalculator
import io.split.client.thin.SplitClient
import io.split.client.thin.Target
import io.split.client.thin.events.EventTracker
import io.split.client.thin.internal.evaluation.EvaluationReadStorage
import io.split.client.thin.internal.evaluation.EvaluationRepository
import io.split.client.thin.internal.secure.EvaluationFilters
import io.harness.events.EventsManagers
import io.split.client.thin.internal.observer.CompositeObserver
import io.split.client.thin.internal.sdkevents.EventManagerObserver
import io.split.client.thin.internal.sdkevents.SplitEventDelivery
import io.split.client.thin.internal.sdkevents.ThinClientEventsConfig
import kotlinx.coroutines.CoroutineScope

internal class DefaultClientFactory(
    private val compositeObserver: CompositeObserver,
    private val scope: CoroutineScope,
    private val readStorage: EvaluationReadStorage,
    private val evaluationRepository: EvaluationRepository,
    private val filters: EvaluationFilters?,
    private val fallbackCalculator: FallbackTreatmentsCalculator?,
) : (Target) -> SplitClient {

    override fun invoke(target: Target): SplitClient {
        val eventTracker = EventTracker.create()
        val eventsManager = EventsManagers.create(
            ThinClientEventsConfig.create(),
            SplitEventDelivery(scope),
        )
        val eventManagerObserver = EventManagerObserver(eventsManager)
        compositeObserver.register(eventManagerObserver)
        return DefaultSplitClient(
            target, eventTracker.tracker, readStorage,
            evaluationRepository, filters, fallbackCalculator, eventsManager
        )
    }
}
