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
class EventDaoTest {

    private lateinit var database: ThinClientDatabase
    private lateinit var dao: EventDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ThinClientDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.eventDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insert returns generated id`() {
        val entity = EventEntity(
            0, // Will be auto-generated
            """{"eventType":"click"}""",
            System.currentTimeMillis()
        )

        val id = dao.insert(entity)
        assertTrue(id > 0)
    }

    @Test
    fun `insert multiple events`() {
        val now = System.currentTimeMillis()
        dao.insert(EventEntity(0, """{"eventType":"click"}""", now))
        dao.insert(EventEntity(0, """{"eventType":"view"}""", now + 1000))
        dao.insert(EventEntity(0, """{"eventType":"purchase"}""", now + 2000))

        assertEquals(3, dao.count())
    }

    @Test
    fun `getOldest returns events in FIFO order`() {
        val now = System.currentTimeMillis()
        dao.insert(EventEntity(0, """{"eventType":"first"}""", now))
        dao.insert(EventEntity(0, """{"eventType":"second"}""", now + 1000))
        dao.insert(EventEntity(0, """{"eventType":"third"}""", now + 2000))

        val oldest = dao.getOldest(2)
        assertEquals(2, oldest.size)
        assertTrue(oldest[0].body.contains("first"))
        assertTrue(oldest[1].body.contains("second"))
    }

    @Test
    fun `getOldest with limit larger than available returns all`() {
        val now = System.currentTimeMillis()
        dao.insert(EventEntity(0, """{"eventType":"first"}""", now))
        dao.insert(EventEntity(0, """{"eventType":"second"}""", now + 1000))

        val result = dao.getOldest(10)
        assertEquals(2, result.size)
    }

    @Test
    fun `delete removes specific entities`() {
        val now = System.currentTimeMillis()
        val id1 = dao.insert(EventEntity(0, """{"eventType":"first"}""", now))
        val id2 = dao.insert(EventEntity(0, """{"eventType":"second"}""", now + 1000))
        val id3 = dao.insert(EventEntity(0, """{"eventType":"third"}""", now + 2000))

        val toDelete = dao.getOldest(2)
        dao.delete(toDelete)

        assertEquals(1, dao.count())
        val remaining = dao.getOldest(10)
        assertTrue(remaining[0].body.contains("third"))
    }

    @Test
    fun `deleteAll removes all events`() {
        val now = System.currentTimeMillis()
        dao.insert(EventEntity(0, """{"eventType":"first"}""", now))
        dao.insert(EventEntity(0, """{"eventType":"second"}""", now + 1000))
        dao.insert(EventEntity(0, """{"eventType":"third"}""", now + 2000))

        dao.deleteAll()

        assertEquals(0, dao.count())
    }

    @Test
    fun `count returns correct number`() {
        assertEquals(0, dao.count())

        val now = System.currentTimeMillis()
        dao.insert(EventEntity(0, """{"eventType":"first"}""", now))
        assertEquals(1, dao.count())

        dao.insert(EventEntity(0, """{"eventType":"second"}""", now + 1000))
        assertEquals(2, dao.count())
    }
}
