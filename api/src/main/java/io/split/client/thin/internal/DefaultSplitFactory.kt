package io.split.client.thin.internal

import io.split.android.client.fallback.FallbackTreatmentsCalculator
import io.split.android.client.fallback.FallbackTreatmentsCalculatorImpl
import io.split.client.thin.SplitClient
import io.split.client.thin.SplitClientConfig
import io.split.client.thin.SplitFactory
import io.split.client.thin.SplitManager
import io.split.client.thin.SplitVoidCallback
import io.split.client.thin.Target
import io.split.client.thin.internal.evaluation.EvaluationReadStorage
import io.split.client.thin.internal.evaluation.EvaluationRepository
import io.split.client.thin.internal.secure.EvaluationFilters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal class DefaultSplitFactory(
    private val defaultTarget: Target,
    private val config: SplitClientConfig?,
    private val asyncBridge: AsyncBridgeLike,
    private val evaluationRepository: EvaluationRepository,
    private val filters: EvaluationFilters?,
    private val readStorage: EvaluationReadStorage,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val clientManager: ClientManager = DefaultClientManager(
        scope,
        DefaultClientFactory(
            readStorage,
            evaluationRepository,
            filters,
            buildFallbackCalculator(config),
        ),
    ),
    private val splitManager: SplitManager = DefaultSplitManager(),
) : SplitFactory {

    init {
        scope.launch {
            runCatching {
                evaluationRepository.setTarget(defaultTarget, filters)
            }
        }
    }

    override fun getClient(target: Target?): SplitClient {
        return clientManager.getOrCreate(target ?: defaultTarget)
    }

    override fun getManager(): SplitManager {
        return splitManager
    }

    override suspend fun destroy() {
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
            return FallbackTreatmentsCalculatorImpl(fbConfig)
        }
    }
}
