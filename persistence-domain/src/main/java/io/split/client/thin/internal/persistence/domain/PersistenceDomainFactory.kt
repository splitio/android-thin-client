package io.split.client.thin.internal.persistence.domain

import android.content.Context
import io.split.android.client.submitter.RecorderStorage
import io.split.android.client.tracker.TrackerEvent
import io.split.client.thin.events.EventsStorage
import io.split.client.thin.internal.persistence.RoomEvaluationPersistence
import io.split.client.thin.internal.persistence.RoomEventsPersistence
import io.split.client.thin.internal.persistence.ThinClientDatabase
import io.split.client.thin.internal.persistence.domain.evaluation.DefaultEvaluationPersistenceManager
import io.split.client.thin.internal.persistence.domain.evaluation.EvaluationPersistenceCallbacks
import io.split.client.thin.internal.persistence.domain.evaluation.EvaluationPersistenceManager
import io.split.client.thin.internal.persistence.domain.evaluation.StoredEvaluationSerializer
import io.split.client.thin.internal.persistence.domain.evaluation.TargetHasher
import io.split.client.thin.internal.persistence.domain.events.EventsPersistenceCallbacks
import io.split.client.thin.internal.persistence.domain.events.PersistentEventsStorage
import io.split.client.thin.internal.persistence.domain.events.TrackerEventSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

data class PersistenceDomainComponents(
    val evaluationPersistenceManager: EvaluationPersistenceManager?,
    val eventsStorage: RecorderStorage<TrackerEvent>
)

fun createPersistenceDomainComponents(
    context: Context,
    config: PersistenceConfig,
    evaluationCallbacks: EvaluationPersistenceCallbacks,
    eventsCallbacks: EventsPersistenceCallbacks,
    scope: CoroutineScope
): PersistenceDomainComponents {
    if (!config.enabled) {
        return PersistenceDomainComponents(
            evaluationPersistenceManager = null,
            eventsStorage = EventsStorage()
        )
    }

    val database = ThinClientDatabase.build(context, config.prefix, config.sdkKey)

    val roomEvalPersistence = RoomEvaluationPersistence(database = database)

    runBlocking {
        withContext(Dispatchers.IO) {
            runCatching {
                val changed = ConfigChangeDetector(database.generalPropertiesDao())
                    .detectAndUpdate(config.dynamicConfig)
                if (changed) roomEvalPersistence.clearAll()
            }
        }
    }

    val targetHasher = TargetHasher()
    val evalSerializer = StoredEvaluationSerializer()
    val eventSerializer = TrackerEventSerializer()

    val evaluationPersistenceManager = DefaultEvaluationPersistenceManager(
        persistentStorage = roomEvalPersistence,
        callbacks = evaluationCallbacks,
        targetHasher = targetHasher,
        evalSerializer = evalSerializer,
        scope = scope
    )

    val eventsStorage = PersistentEventsStorage(
        roomEventsPersistence = RoomEventsPersistence(dao = database.eventDao()),
        serializer = eventSerializer,
        callbacks = eventsCallbacks,
    )

    return PersistenceDomainComponents(
        evaluationPersistenceManager = evaluationPersistenceManager,
        eventsStorage = eventsStorage
    )
}
