package io.split.client.thin.internal.persistence

import io.split.client.thin.internal.observer.CompositeObserver
import io.split.client.thin.internal.observer.ObservableEvent
import io.split.client.thin.internal.observer.ObservableEventType
import io.split.client.thin.internal.persistence.domain.events.EventsPersistenceCallbacks

internal class ObserverEventsPersistenceCallbacks(
    private val compositeObserver: CompositeObserver
) : EventsPersistenceCallbacks {

    override fun onEventPushed() {
        compositeObserver.notifyEvent(ObservableEvent(ObservableEventType.EVENT_PUSHED))
    }

    override fun onEventPopped(count: Int) {
        compositeObserver.notifyEvent(ObservableEvent(ObservableEventType.EVENT_POPPED))
    }

    override fun onPersistenceFailed(error: String) {
        compositeObserver.notifyEvent(ObservableEvent(ObservableEventType.PERSISTENCE_FAILED))
    }
}
