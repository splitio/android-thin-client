package io.split.client.thin.internal.persistence

import io.split.android.client.tracker.TrackerEvent

interface PersistentEventsStorage {
    fun push(event: TrackerEvent)
    fun pop(count: Int): List<TrackerEvent>
    fun clear()
    fun count(): Int
}
