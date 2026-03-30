package io.split.client.thin.internal.persistence.domain

import android.content.Context
import io.split.android.client.submitter.RecorderStorage
import io.split.android.client.tracker.TrackerEvent
import io.split.client.thin.events.EventsStorage
import io.split.client.thin.internal.persistence.RoomEvaluationPersistence
import io.split.client.thin.internal.persistence.RoomEventsPersistence
import io.split.client.thin.internal.persistence.ThinClientDatabase
import io.split.client.thin.internal.persistence.domain.evaluation.AttributesSerializer
import io.split.client.thin.internal.persistence.domain.evaluation.DefaultEvaluationPersistenceManager
import io.split.client.thin.internal.persistence.domain.evaluation.EvaluationKeySerializer
import io.split.client.thin.internal.persistence.domain.evaluation.EvaluationPersistenceCallbacks
import io.split.client.thin.internal.persistence.domain.evaluation.EvaluationPersistenceManager
import io.split.client.thin.internal.persistence.domain.evaluation.StoredEvaluationSerializer
import io.split.client.thin.internal.persistence.domain.events.EventsPersistenceCallbacks
import io.split.client.thin.internal.persistence.domain.events.PersistentEventsStorage
import io.split.client.thin.internal.persistence.domain.events.TrackerEventSerializer
import kotlinx.coroutines.CoroutineScope

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

    val database = ThinClientDatabase.build(context, config.prefix)

    val keySerializer = EvaluationKeySerializer()
    val attributesSerializer = AttributesSerializer()
    val evalSerializer = StoredEvaluationSerializer()
    val eventSerializer = TrackerEventSerializer()

    val evaluationPersistenceManager = DefaultEvaluationPersistenceManager(
        persistentStorage = RoomEvaluationPersistence(
            evaluationDao = database.evaluationDao(),
            metadataDao = database.evaluationMetadataDao()
        ),
        attributesDao = database.attributesDao(),
        callbacks = evaluationCallbacks,
        keySerializer = keySerializer,
        attributesSerializer = attributesSerializer,
        evalSerializer = evalSerializer,
        scope = scope
    )

    val eventsStorage = PersistentEventsStorage(
        roomEventsPersistence = RoomEventsPersistence(dao = database.eventDao()),
        serializer = eventSerializer,
        callbacks = eventsCallbacks,
        scope = scope
    )

    return PersistenceDomainComponents(
        evaluationPersistenceManager = evaluationPersistenceManager,
        eventsStorage = eventsStorage
    )
}
