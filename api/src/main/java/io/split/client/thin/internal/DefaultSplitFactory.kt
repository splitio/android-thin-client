package io.split.client.thin.internal

import io.split.android.client.fallback.FallbackTreatmentsCalculator
import io.split.android.client.fallback.FallbackTreatmentsCalculatorImpl
import io.split.client.thin.internal.toInternal
import io.split.client.thin.SplitClient
import io.split.client.thin.SplitClientConfig
import io.split.client.thin.SplitFactory
import io.split.client.thin.SplitManager
import io.split.client.thin.SplitVoidCallback
import io.split.client.thin.Target
import io.split.client.thin.events.EventSubmissionCoordinator
import io.split.client.thin.events.EventsPeriodicScheduler
import io.split.client.thin.internal.evaluation.DefaultEvaluationPeriodicScheduler
import io.split.client.thin.internal.evaluation.EvaluationFetchCoordinator
import io.split.client.thin.internal.evaluation.EvaluationRepository
import io.split.client.thin.internal.evaluation.toEvaluationKey
import io.split.client.thin.internal.secure.EvaluationFilters
import io.split.client.thin.internal.observer.DefaultCompositeObserver
import io.split.client.thin.internal.observer.ObservableEvent
import io.split.client.thin.internal.observer.ObservableEventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class DefaultSplitFactory(
    private val defaultTarget: Target,
    private val config: SplitClientConfig?,
    private val asyncBridge: AsyncBridgeLike,
    private val evaluationRepository: EvaluationRepository,
    private val filters: EvaluationFilters?,
    private val fetchCoordinator: EvaluationFetchCoordinator,
    private val schedulerIntervalMillis: Long,
    private val eventsScheduler: EventsPeriodicScheduler? = null,
    private val eventsCoordinator: EventSubmissionCoordinator? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val compositeObserver: DefaultCompositeObserver = DefaultCompositeObserver(),
    private val clientManager: ClientManager = DefaultClientManager(
        scope,
        DefaultClientFactory(
            compositeObserver = compositeObserver,
            scope = scope,
            evaluationRepository = evaluationRepository,
            filters = filters,
            fallbackCalculator = buildFallbackCalculator(config),
            fetchCoordinator = fetchCoordinator,
            schedulerIntervalMillis = schedulerIntervalMillis,
            schedulerFactory = { coordinator, intervalMillis ->
                DefaultEvaluationPeriodicScheduler(
                    fetchCoordinator = coordinator,
                    intervalMillis = intervalMillis,
                    onPollTrigger = { interval ->
                        compositeObserver.notifyEvent(
                            ObservableEvent(
                                type = ObservableEventType.POLL_TRIGGER,
                                properties = mapOf("rate" to "${interval / 1000}s")
                            )
                        )
                    }
                )
            },
        ),
    ),
    private val splitManager: SplitManager = DefaultSplitManager(evaluationRepository, defaultTarget.toEvaluationKey()),
) : SplitFactory {

    init {
        scope.launch {
            runCatching {
                evaluationRepository.setTarget(defaultTarget, filters)
            }
        }

        val timeoutSecs = config?.storage?.timeout ?: -1
        if (timeoutSecs > 0) {
            scope.launch {
                delay(timeoutSecs * 1_000L)
                compositeObserver.notifyEvent(
                    ObservableEvent(ObservableEventType.SDK_READY_TIMEOUT_REACHED)
                )
            }
        }

        eventsScheduler?.start()
    }

    override fun getClient(target: Target?): SplitClient {
        return clientManager.getOrCreate(target ?: defaultTarget)
    }

    override fun getManager(): SplitManager {
        return splitManager
    }

    override suspend fun destroy() {
        eventsScheduler?.stop()
        eventsCoordinator?.flush()
        compositeObserver.unregisterAll()
        clientManager.destroyAll()
        scope.cancel()
        asyncBridge.close()
    }

    @Deprecated("Use suspend destroy()", level = DeprecationLevel.ERROR)
    override fun destroyAsync(callback: SplitVoidCallback) =
        asyncBridge.executeAsync(callback) { destroy() }

    companion object {
        fun buildFallbackCalculator(config: SplitClientConfig?): FallbackTreatmentsCalculator? {
            val fbConfig = config?.fallbackTreatments ?: return null
            return FallbackTreatmentsCalculatorImpl(fbConfig.toInternal())
        }
    }
}
