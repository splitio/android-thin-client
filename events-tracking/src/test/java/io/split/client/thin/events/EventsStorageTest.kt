package io.split.client.thin.events

import io.split.android.client.tracker.TrackerEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EventsStorageTest {

    private lateinit var storage: EventsStorage

    @Before
    fun setUp() {
        storage = EventsStorage()
    }

    @Test
    fun `push adds event to storage`() {
        val event = createEvent("event1")

        storage.push(event)

        val popped = storage.pop(10)
        assertEquals(1, popped.size)
        assertEquals("event1", popped[0].eventType)
    }

    @Test
    fun `pop returns up to count items`() {
        storage.push(createEvent("event1"))
        storage.push(createEvent("event2"))
        storage.push(createEvent("event3"))

        val popped = storage.pop(2)

        assertEquals(2, popped.size)
        assertEquals("event1", popped[0].eventType)
        assertEquals("event2", popped[1].eventType)
    }

    @Test
    fun `pop removes items from storage`() {
        storage.push(createEvent("event1"))
        storage.push(createEvent("event2"))

        storage.pop(1)
        val remaining = storage.pop(10)

        assertEquals(1, remaining.size)
        assertEquals("event2", remaining[0].eventType)
    }

    @Test
    fun `pop returns empty list when storage is empty`() {
        val popped = storage.pop(5)

        assertTrue(popped.isEmpty())
    }

    @Test
    fun `setActive re-queues items`() {
        val event1 = createEvent("event1")
        val event2 = createEvent("event2")
        storage.push(event1)
        storage.push(event2)

        val popped = storage.pop(2)
        storage.setActive(popped)

        val requeued = storage.pop(10)
        assertEquals(2, requeued.size)
    }

    @Test
    fun `delete is a no-op`() {
        val event = createEvent("event1")
        storage.push(event)
        val popped = storage.pop(1)

        storage.delete(popped)

        val remaining = storage.pop(10)
        assertTrue(remaining.isEmpty())
    }

    @Test
    fun `storage is thread-safe`() {
        val threads = (1..10).map { threadId ->
            Thread {
                repeat(100) { i ->
                    storage.push(createEvent("thread-$threadId-event-$i"))
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        var totalCount = 0
        while (true) {
            val batch = storage.pop(100)
            if (batch.isEmpty()) break
            totalCount += batch.size
        }

        assertEquals(1000, totalCount)
    }

    private fun createEvent(eventType: String): TrackerEvent {
        return TrackerEvent().apply {
            this.eventType = eventType
            this.key = "test-key"
            this.trafficType = "user"
            this.value = 0.0
            this.timestamp = System.currentTimeMillis()
            this.properties = emptyMap()
            this.sizeInBytes = 100
        }
    }
}
