package io.split.client.thin.internal

import io.split.android.client.fallback.FallbackTreatmentsCalculator
import io.split.client.thin.SplitClient
import io.split.client.thin.Target
import io.split.client.thin.events.EventTracker
import io.split.client.thin.internal.evaluation.DefaultEvaluationPeriodicScheduler
import io.split.client.thin.internal.evaluation.EvaluationFetchCoordinator
import io.split.client.thin.internal.evaluation.EvaluationPeriodicScheduler
import io.split.client.thin.internal.evaluation.EvaluationRepository
import io.split.client.thin.internal.secure.EvaluationFilters
import io.harness.events.EventsManagers
import io.split.android.client.tracker.DefaultTracker
import io.split.client.thin.internal.observer.CompositeObserver
import io.split.client.thin.internal.sdkevents.EventManagerObserver
import io.split.client.thin.internal.sdkevents.SplitEventDelivery
import io.split.client.thin.internal.sdkevents.ThinClientEventsConfig
import kotlinx.coroutines.CoroutineScope

internal class DefaultClientFactory(
    private val compositeObserver: CompositeObserver,
    private val scope: CoroutineScope,
    private val evaluationRepository: EvaluationRepository,
    private val filters: EvaluationFilters?,
    private val fallbackCalculator: FallbackTreatmentsCalculator?,
    private val fetchCoordinator: EvaluationFetchCoordinator,
    private val schedulerIntervalMillis: Long,
    private val onEventPush: DefaultTracker.OnEventPush = DefaultTracker.OnEventPush { },
    private val flushFn: suspend () -> Unit = {},
    private val schedulerFactory: (EvaluationFetchCoordinator, Long) -> EvaluationPeriodicScheduler = { coordinator, intervalMillis ->
        DefaultEvaluationPeriodicScheduler(fetchCoordinator = coordinator, intervalMillis = intervalMillis)
    },
) : (Target) -> SplitClient {

    override fun invoke(target: Target): SplitClient {
        val eventTracker = EventTracker.create(onEventPush)
        val eventsManager = EventsManagers.create(
            ThinClientEventsConfig.create(),
            SplitEventDelivery(scope),
        )
        val eventManagerObserver = EventManagerObserver(eventsManager)
        compositeObserver.register(eventManagerObserver)
        val scheduler = schedulerFactory(fetchCoordinator, schedulerIntervalMillis)
        scheduler.start(target, filters)
        return DefaultSplitClient(
            target, eventTracker.tracker,
            evaluationRepository, filters, fallbackCalculator, eventsManager, scheduler, flushFn
        )
    }
}
