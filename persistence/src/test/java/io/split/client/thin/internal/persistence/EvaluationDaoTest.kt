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
            "keyHash1",
            "feature_flag",
            """{"treatment":"on"}"""
        )

        dao.insert(listOf(entity))

        val results = dao.getByKey("keyHash1")
        assertEquals(1, results.size)
        assertEquals("keyHash1", results[0].keyHash)
        assertEquals("feature_flag", results[0].flagName)
        assertEquals("""{"treatment":"on"}""", results[0].evalJson)
    }

    @Test
    fun `insert multiple entities for same keyHash`() {
        val entities = listOf(
            EvaluationEntity("keyHash1", "flag1", """{"treatment":"on"}"""),
            EvaluationEntity("keyHash1", "flag2", """{"treatment":"off"}"""),
            EvaluationEntity("keyHash1", "flag3", """{"treatment":"control"}""")
        )

        dao.insert(entities)

        val results = dao.getByKey("keyHash1")
        assertEquals(3, results.size)
        assertEquals(setOf("flag1", "flag2", "flag3"), results.map { it.flagName }.toSet())
    }

    @Test
    fun `insert with REPLACE on conflict`() {
        val entity1 = EvaluationEntity("keyHash1", "flag1", """{"treatment":"on"}""")
        dao.insert(listOf(entity1))

        val entity2 = EvaluationEntity("keyHash1", "flag1", """{"treatment":"off"}""")
        dao.insert(listOf(entity2))

        val results = dao.getByKey("keyHash1")
        assertEquals(1, results.size)
        assertEquals("""{"treatment":"off"}""", results[0].evalJson)
    }

    @Test
    fun `getByKey returns empty list when no match`() {
        val results = dao.getByKey("nonexistent")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `getByKey only returns entities for matching keyHash`() {
        dao.insert(listOf(
            EvaluationEntity("keyHash1", "flag1", """{"treatment":"on"}"""),
            EvaluationEntity("keyHash2", "flag1", """{"treatment":"control"}""")
        ))

        val results = dao.getByKey("keyHash1")
        assertEquals(1, results.size)
        assertEquals("keyHash1", results[0].keyHash)
    }

    @Test
    fun `deleteByKeyHash removes all rows for keyHash`() {
        dao.insert(listOf(
            EvaluationEntity("keyHash1", "flag1", """{"treatment":"on"}"""),
            EvaluationEntity("keyHash1", "flag2", """{"treatment":"off"}"""),
            EvaluationEntity("keyHash2", "flag1", """{"treatment":"control"}""")
        ))

        dao.deleteByKeyHash("keyHash1")

        assertTrue(dao.getByKey("keyHash1").isEmpty())
        assertEquals(1, dao.getByKey("keyHash2").size)
    }

    @Test
    fun `replaceForKey is atomic transaction`() {
        dao.insert(listOf(
            EvaluationEntity("keyHash1", "flag1", """{"treatment":"on"}"""),
            EvaluationEntity("keyHash1", "flag2", """{"treatment":"off"}""")
        ))

        val newEntities = listOf(
            EvaluationEntity("keyHash1", "flag3", """{"treatment":"control"}"""),
            EvaluationEntity("keyHash1", "flag4", """{"treatment":"on"}""")
        )

        dao.deleteByKeyHash("keyHash1")
        dao.insert(newEntities)

        val results = dao.getByKey("keyHash1")
        assertEquals(2, results.size)
        assertEquals(setOf("flag3", "flag4"), results.map { it.flagName }.toSet())
    }
}
