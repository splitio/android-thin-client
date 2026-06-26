package io.split.client.thin.internal.persistence

interface PersistentEventsStorage {
    fun push(eventJson: String)
    fun pop(count: Int): List<StoredEvent>
    fun delete(ids: List<Long>)
    fun clear()
    fun count(): Int
}
