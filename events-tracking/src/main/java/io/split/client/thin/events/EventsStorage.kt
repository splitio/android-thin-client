package io.split.client.thin.events

import io.split.android.client.submitter.RecorderStorage
import io.split.android.client.submitter.StoragePusher
import io.split.android.client.tracker.TrackerEvent

class EventsStorage : RecorderStorage<TrackerEvent>, StoragePusher<TrackerEvent> {

    private val queue = mutableListOf<TrackerEvent>()

    @Synchronized
    override fun push(element: TrackerEvent) {
        queue.add(element)
    }

    @Synchronized
    override fun pop(count: Int): List<TrackerEvent> {
        val itemsToPop = minOf(count, queue.size)
        val items = queue.take(itemsToPop)
        repeat(itemsToPop) { queue.removeAt(0) }
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
