package io.split.client.thin.internal.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.split.android.client.tracker.TrackerEvent
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoomEventsPersistenceTest {

    private lateinit var database: ThinClientDatabase
    private lateinit var persistence: RoomEventsPersistence

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ThinClientDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        persistence = RoomEventsPersistence(database.eventDao())
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `push adds event to storage`() {
        val event = TrackerEvent().apply {
            trafficType = "user"
            eventType = "click"
            key = "user1"
            value = 1.0
            timestamp = System.currentTimeMillis()
            properties = mapOf("button" to "submit")
            sizeInBytes = 100
        }

        persistence.push(event)

        assertEquals(1, persistence.count())
    }

    @Test
    fun `push multiple events`() {
        val event1 = createEvent("click", "user1", 1.0)
        val event2 = createEvent("view", "user2", 2.0)
        val event3 = createEvent("purchase", "user3", 3.0)

        persistence.push(event1)
        persistence.push(event2)
        persistence.push(event3)

        assertEquals(3, persistence.count())
    }

    @Test
    fun `pop returns events in FIFO order`() {
        val event1 = createEvent("click", "user1", 1.0)
        val event2 = createEvent("view", "user2", 2.0)
        val event3 = createEvent("purchase", "user3", 3.0)

        persistence.push(event1)
        Thread.sleep(10)
        persistence.push(event2)
        Thread.sleep(10)
        persistence.push(event3)

        val popped = persistence.pop(2)
        assertEquals(2, popped.size)
        assertEquals("click", popped[0].eventType)
        assertEquals("view", popped[1].eventType)
        assertEquals(1, persistence.count())
    }

    @Test
    fun `pop and push round trip preserves event data`() {
        val original = TrackerEvent().apply {
            trafficType = "user"
            eventType = "purchase"
            key = "user123"
            value = 99.99
            timestamp = System.currentTimeMillis()
            properties = mapOf("item" to "widget", "quantity" to 5)
            sizeInBytes = 150
        }

        persistence.push(original)
        val popped = persistence.pop(1)

        assertEquals(1, popped.size)
        val restored = popped[0]
        assertEquals(original.trafficType, restored.trafficType)
        assertEquals(original.eventType, restored.eventType)
        assertEquals(original.key, restored.key)
        assertEquals(original.value, restored.value, 0.001)
        assertEquals(original.timestamp, restored.timestamp)
        // Gson deserializes integers as doubles in Map<String, Object>
        assertEquals("widget", restored.properties["item"])
        assertEquals(5.0, restored.properties["quantity"])
        assertEquals(original.sizeInBytes, restored.sizeInBytes)
    }

    @Test
    fun `pop with limit larger than available returns all`() {
        persistence.push(createEvent("click", "user1", 1.0))
        persistence.push(createEvent("view", "user2", 2.0))

        val popped = persistence.pop(10)
        assertEquals(2, popped.size)
        assertEquals(0, persistence.count())
    }

    @Test
    fun `pop from empty storage returns empty list`() {
        val popped = persistence.pop(10)
        assertTrue(popped.isEmpty())
    }

    @Test
    fun `clear removes all events`() {
        persistence.push(createEvent("click", "user1", 1.0))
        persistence.push(createEvent("view", "user2", 2.0))
        persistence.push(createEvent("purchase", "user3", 3.0))

        persistence.clear()

        assertEquals(0, persistence.count())
    }

    @Test
    fun `count returns accurate number`() {
        assertEquals(0, persistence.count())

        persistence.push(createEvent("click", "user1", 1.0))
        assertEquals(1, persistence.count())

        persistence.push(createEvent("view", "user2", 2.0))
        assertEquals(2, persistence.count())

        persistence.pop(1)
        assertEquals(1, persistence.count())

        persistence.clear()
        assertEquals(0, persistence.count())
    }

    private fun createEvent(eventType: String, key: String, value: Double): TrackerEvent {
        return TrackerEvent().apply {
            this.trafficType = "user"
            this.eventType = eventType
            this.key = key
            this.value = value
            this.timestamp = System.currentTimeMillis()
            this.properties = emptyMap()
            this.sizeInBytes = 50
        }
    }
}
