package io.split.client.thin.internal.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
        persistence.push(createEventJson("click", "user1", 1.0))
        assertEquals(1, persistence.count())
    }

    @Test
    fun `push multiple events`() {
        persistence.push(createEventJson("click", "user1", 1.0))
        persistence.push(createEventJson("view", "user2", 2.0))
        persistence.push(createEventJson("purchase", "user3", 3.0))
        assertEquals(3, persistence.count())
    }

    @Test
    fun `pop returns events in FIFO order without removing them`() {
        val event1Json = createEventJson("click", "user1", 1.0)
        val event2Json = createEventJson("view", "user2", 2.0)
        val event3Json = createEventJson("purchase", "user3", 3.0)

        persistence.push(event1Json)
        Thread.sleep(10)
        persistence.push(event2Json)
        Thread.sleep(10)
        persistence.push(event3Json)

        val popped = persistence.pop(2)
        assertEquals(2, popped.size)
        assertTrue(popped[0].json.contains("\"click\""))
        assertTrue(popped[1].json.contains("\"view\""))
        assertEquals(3, persistence.count())
    }

    @Test
    fun `pop does not remove events from storage`() {
        persistence.push(createEventJson("click", "user1", 1.0))

        persistence.pop(1)

        assertEquals(1, persistence.count())
    }

    @Test
    fun `pop returns StoredEvent with valid id and json`() {
        val eventJson = createEventJson("click", "user1", 1.0)
        persistence.push(eventJson)

        val popped = persistence.pop(1)

        assertEquals(1, popped.size)
        assertTrue(popped[0].id > 0)
        assertEquals(eventJson, popped[0].json)
    }

    @Test
    fun `delete removes specific events by id`() {
        val event1Json = createEventJson("click", "user1", 1.0)
        val event2Json = createEventJson("view", "user2", 2.0)

        persistence.push(event1Json)
        persistence.push(event2Json)

        val popped = persistence.pop(1)
        persistence.delete(popped.map { it.id })

        assertEquals(1, persistence.count())
        val remaining = persistence.pop(10)
        assertEquals(1, remaining.size)
        assertTrue(remaining[0].json.contains("\"view\""))
    }

    @Test
    fun `pop then delete removes only confirmed events`() {
        val event1Json = createEventJson("click", "user1", 1.0)
        val event2Json = createEventJson("view", "user2", 2.0)

        persistence.push(event1Json)
        persistence.push(event2Json)

        val popped = persistence.pop(1)
        assertEquals(2, persistence.count())

        persistence.delete(popped.map { it.id })
        assertEquals(1, persistence.count())
    }

    @Test
    fun `pop with limit larger than available returns all`() {
        persistence.push(createEventJson("click", "user1", 1.0))
        persistence.push(createEventJson("view", "user2", 2.0))

        val popped = persistence.pop(10)
        assertEquals(2, popped.size)
        assertEquals(2, persistence.count())
    }

    @Test
    fun `pop from empty storage returns empty list`() {
        assertTrue(persistence.pop(10).isEmpty())
    }

    @Test
    fun `clear removes all events`() {
        persistence.push(createEventJson("click", "user1", 1.0))
        persistence.push(createEventJson("view", "user2", 2.0))

        persistence.clear()

        assertEquals(0, persistence.count())
    }

    @Test
    fun `count returns accurate number`() {
        assertEquals(0, persistence.count())

        persistence.push(createEventJson("click", "user1", 1.0))
        assertEquals(1, persistence.count())

        persistence.push(createEventJson("view", "user2", 2.0))
        assertEquals(2, persistence.count())

        val popped = persistence.pop(1)
        assertEquals(2, persistence.count())

        persistence.delete(popped.map { it.id })
        assertEquals(1, persistence.count())

        persistence.clear()
        assertEquals(0, persistence.count())
    }

    private fun createEventJson(eventType: String, key: String, value: Double): String {
        return """{"trafficType":"user","eventType":"$eventType","key":"$key","value":$value,"timestamp":${System.currentTimeMillis()},"properties":{},"sizeInBytes":50}"""
    }
}
