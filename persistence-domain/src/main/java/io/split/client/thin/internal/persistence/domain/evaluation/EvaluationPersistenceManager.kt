package io.split.client.thin.internal.persistence.domain.evaluation

import io.split.client.thin.internal.evaluation.EvaluationChange
import io.split.client.thin.internal.evaluation.EvaluationKey

interface EvaluationPersistenceManager {
    suspend fun loadLocal(evalKey: EvaluationKey): EvaluationChange?
}
