package io.split.client.thin.internal.persistence

interface PersistentEventsStorage {
    fun push(eventJson: String)
    fun pop(count: Int): List<String>
    fun clear()
    fun count(): Int
}
