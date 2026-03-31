package io.split.client.thin.internal

import io.harness.events.EventsManagers
import io.split.android.client.fallback.FallbackTreatmentsCalculator
import io.split.android.client.tracker.DefaultTracker
import io.split.client.thin.SplitClient
import io.split.client.thin.Target
import io.split.client.thin.events.EventTracker
import io.split.client.thin.internal.evaluation.DefaultEvaluationPeriodicScheduler
import io.split.client.thin.internal.evaluation.EvaluationFetchCoordinator
import io.split.client.thin.internal.evaluation.EvaluationPeriodicScheduler
import io.split.client.thin.internal.evaluation.EvaluationRepository
import io.split.client.thin.internal.lifecycle.LifecycleComponent
import io.split.client.thin.internal.lifecycle.LifecycleManager
import io.split.client.thin.internal.observer.CompositeObserver
import io.split.client.thin.internal.sdkevents.EventManagerObserver
import io.split.client.thin.internal.sdkevents.SplitEventDelivery
import io.split.client.thin.internal.sdkevents.ThinClientEventsConfig
import io.split.client.thin.internal.secure.EvaluationFilters
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicBoolean

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
    private val lifecycleManager: LifecycleManager? = null,
    private val pollingEnabled: AtomicBoolean = AtomicBoolean(true),
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
        if (pollingEnabled.get()) {
            scheduler.start(target, filters)
        }
        lifecycleManager?.register(object : LifecycleComponent {
            override fun pause() = scheduler.pause()
            override fun resume() = scheduler.resume()
        })
        return DefaultSplitClient(
            initialTarget = target,
            tracker = eventTracker.tracker,
            evaluationRepository = evaluationRepository,
            filters = filters,
            fallbackCalculator = fallbackCalculator,
            eventsManager = eventsManager,
            periodicScheduler = scheduler,
            flushOperation = flushFn,
            scope = scope,
        )
    }
}
