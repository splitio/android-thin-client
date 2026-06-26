package io.split.client.thin.internal.evaluation

import io.split.client.thin.Key
import io.split.client.thin.Target

data class EvaluationKey(
    val key: Key,
    val attributes: Map<String, Any?> = emptyMap(),
)

fun Target.toEvaluationKey() = EvaluationKey(key, attributes)
