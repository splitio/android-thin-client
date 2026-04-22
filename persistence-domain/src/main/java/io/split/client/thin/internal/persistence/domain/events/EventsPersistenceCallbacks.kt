package io.split.client.thin.internal.persistence.domain.events

interface EventsPersistenceCallbacks {
    fun onEventPushed()
    fun onEventPopped(count: Int)
    fun onPersistenceFailed(error: String)
}
