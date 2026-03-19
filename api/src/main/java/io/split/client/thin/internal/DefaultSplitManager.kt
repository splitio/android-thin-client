package io.split.client.thin.internal

import io.split.client.thin.SplitManager
import io.split.client.thin.internal.evaluation.EvaluationKey
import io.split.client.thin.internal.evaluation.EvaluationReadStorage

internal class DefaultSplitManager(
    private val readStorage: EvaluationReadStorage,
    private val defaultEvalKey: EvaluationKey,
) : SplitManager {
    override val flagNames: List<String>
        get() = readStorage.getFlagNames(defaultEvalKey).toList()
}
