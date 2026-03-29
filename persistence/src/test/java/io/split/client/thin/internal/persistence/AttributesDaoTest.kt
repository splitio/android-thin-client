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
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AttributesDaoTest {

    private lateinit var database: ThinClientDatabase
    private lateinit var dao: AttributesDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ThinClientDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.attributesDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insert and retrieve attributes`() {
        val entity = AttributesEntity(
            "user1",
            """{"plan":"premium","role":"admin"}""",
            System.currentTimeMillis()
        )

        dao.insert(entity)

        val result = dao.getByKey("user1")
        assertEquals("user1", result.key)
        assertEquals("""{"plan":"premium","role":"admin"}""", result.json)
    }

    @Test
    fun `insert with REPLACE on conflict`() {
        dao.insert(AttributesEntity("user1", """{"old":"value"}""", System.currentTimeMillis()))
        dao.insert(AttributesEntity("user1", """{"new":"value"}""", System.currentTimeMillis() + 1000))

        val result = dao.getByKey("user1")
        assertEquals("""{"new":"value"}""", result.json)
    }

    @Test
    fun `getByKey returns null when key does not exist`() {
        assertNull(dao.getByKey("nonexistent"))
    }

    @Test
    fun `deleteByKey removes attributes`() {
        dao.insert(AttributesEntity("user1", """{"data":"value"}""", System.currentTimeMillis()))

        dao.deleteByKey("user1")

        assertNull(dao.getByKey("user1"))
    }

    @Test
    fun `different keys store different attributes`() {
        dao.insert(AttributesEntity("user1", """{"plan":"free"}""", System.currentTimeMillis()))
        dao.insert(AttributesEntity("user2", """{"plan":"premium"}""", System.currentTimeMillis()))

        assertEquals("""{"plan":"free"}""", dao.getByKey("user1").json)
        assertEquals("""{"plan":"premium"}""", dao.getByKey("user2").json)
    }
}
