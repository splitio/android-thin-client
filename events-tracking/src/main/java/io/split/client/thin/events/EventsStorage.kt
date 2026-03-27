package io.split.client.thin.events

import io.split.android.client.submitter.RecorderStorage
import io.split.android.client.submitter.StoragePusher
import io.split.android.client.tracker.TrackerEvent

/**
 * In memory storage for events. Soon to be backed by persistent storage.
 */
class EventsStorage : RecorderStorage<TrackerEvent>, StoragePusher<TrackerEvent> {

    private val queue = mutableListOf<TrackerEvent>()

    @Synchronized
    override fun push(element: TrackerEvent) {
        queue.add(element)
    }

    @Synchronized
    override fun pop(count: Int): List<TrackerEvent> {
        val itemsToPop = minOf(count, queue.size)
        val items = queue.subList(0, itemsToPop).toList()
        queue.subList(0, itemsToPop).clear()
        return items
    }

    @Synchronized
    override fun delete(items: List<TrackerEvent>) {
        // No-op: items already removed by pop
    }

    @Synchronized
    override fun setActive(items: List<TrackerEvent>) {
        queue.addAll(0, items)
    }
}
