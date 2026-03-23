package io.split.client.thin.internal

import io.split.client.thin.SplitManager
import io.split.client.thin.internal.evaluation.EvaluationKey
import io.split.client.thin.internal.evaluation.EvaluationRepository

internal class DefaultSplitManager(
    private val repository: EvaluationRepository,
    private val defaultEvalKey: EvaluationKey,
) : SplitManager {
    override val flagNames: List<String>
        get() = repository.getFlagNames(defaultEvalKey).toList()
}
