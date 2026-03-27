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
class EvaluationDaoTest {

    private lateinit var database: ThinClientDatabase
    private lateinit var dao: EvaluationDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ThinClientDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.evaluationDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insert single entity`() {
        val entity = EvaluationEntity(
            "user1",
            "feature_flag",
            """{"treatment":"on"}""",
            100L,
            System.currentTimeMillis()
        )

        dao.insert(listOf(entity))

        val results = dao.getByKey("user1")
        assertEquals(1, results.size)
        assertEquals("user1", results[0].matchingKey)
        assertEquals("feature_flag", results[0].flagName)
        assertEquals("""{"treatment":"on"}""", results[0].body)
        assertEquals(100L, results[0].changeNumber)
    }

    @Test
    fun `insert multiple entities for same key`() {
        val entities = listOf(
            EvaluationEntity("user1", "flag1", """{"treatment":"on"}""", 100L, System.currentTimeMillis()),
            EvaluationEntity("user1", "flag2", """{"treatment":"off"}""", 100L, System.currentTimeMillis()),
            EvaluationEntity("user1", "flag3", """{"treatment":"control"}""", 100L, System.currentTimeMillis())
        )

        dao.insert(entities)

        val results = dao.getByKey("user1")
        assertEquals(3, results.size)
        assertEquals(setOf("flag1", "flag2", "flag3"), results.map { it.flagName }.toSet())
    }

    @Test
    fun `insert with REPLACE on conflict`() {
        val entity1 = EvaluationEntity("user1", "flag1", """{"treatment":"on"}""", 100L, System.currentTimeMillis())
        dao.insert(listOf(entity1))

        val entity2 = EvaluationEntity("user1", "flag1", """{"treatment":"off"}""", 200L, System.currentTimeMillis() + 1000)
        dao.insert(listOf(entity2))

        val results = dao.getByKey("user1")
        assertEquals(1, results.size)
        assertEquals("""{"treatment":"off"}""", results[0].body)
        assertEquals(200L, results[0].changeNumber)
    }

    @Test
    fun `getByKey returns empty list when key does not exist`() {
        val results = dao.getByKey("nonexistent")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `getByKey only returns entities for requested key`() {
        dao.insert(listOf(
            EvaluationEntity("user1", "flag1", """{"treatment":"on"}""", 100L, System.currentTimeMillis()),
            EvaluationEntity("user2", "flag1", """{"treatment":"off"}""", 100L, System.currentTimeMillis())
        ))

        val results = dao.getByKey("user1")
        assertEquals(1, results.size)
        assertEquals("user1", results[0].matchingKey)
    }

    @Test
    fun `deleteByKey removes all entities for key`() {
        dao.insert(listOf(
            EvaluationEntity("user1", "flag1", """{"treatment":"on"}""", 100L, System.currentTimeMillis()),
            EvaluationEntity("user1", "flag2", """{"treatment":"off"}""", 100L, System.currentTimeMillis()),
            EvaluationEntity("user2", "flag1", """{"treatment":"control"}""", 100L, System.currentTimeMillis())
        ))

        dao.deleteByKey("user1")

        val user1Results = dao.getByKey("user1")
        assertTrue(user1Results.isEmpty())

        val user2Results = dao.getByKey("user2")
        assertEquals(1, user2Results.size)
    }

    @Test
    fun `replaceForKey is atomic transaction`() {
        dao.insert(listOf(
            EvaluationEntity("user1", "flag1", """{"treatment":"on"}""", 100L, System.currentTimeMillis()),
            EvaluationEntity("user1", "flag2", """{"treatment":"off"}""", 100L, System.currentTimeMillis())
        ))

        val newEntities = listOf(
            EvaluationEntity("user1", "flag3", """{"treatment":"control"}""", 200L, System.currentTimeMillis()),
            EvaluationEntity("user1", "flag4", """{"treatment":"on"}""", 200L, System.currentTimeMillis())
        )

        dao.replaceForKey("user1", newEntities)

        val results = dao.getByKey("user1")
        assertEquals(2, results.size)
        assertEquals(setOf("flag3", "flag4"), results.map { it.flagName }.toSet())
        assertTrue(results.all { it.changeNumber == 200L })
    }
}
