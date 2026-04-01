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
            "attrsHash1",
            """{"treatment":"on"}""",
            System.currentTimeMillis()
        )

        dao.insert(listOf(entity))

        val results = dao.getByKeyAndAttrs("keyHash1", "attrsHash1")
        assertEquals(1, results.size)
        assertEquals("keyHash1", results[0].keyHash)
        assertEquals("attrsHash1", results[0].attrsHash)
        assertEquals("feature_flag", results[0].flagName)
        assertEquals("""{"treatment":"on"}""", results[0].body)
    }

    @Test
    fun `insert multiple entities for same keyHash and attrsHash`() {
        val entities = listOf(
            EvaluationEntity("keyHash1", "flag1", "attrsHash1", """{"treatment":"on"}""", System.currentTimeMillis()),
            EvaluationEntity("keyHash1", "flag2", "attrsHash1", """{"treatment":"off"}""", System.currentTimeMillis()),
            EvaluationEntity("keyHash1", "flag3", "attrsHash1", """{"treatment":"control"}""", System.currentTimeMillis())
        )

        dao.insert(entities)

        val results = dao.getByKeyAndAttrs("keyHash1", "attrsHash1")
        assertEquals(3, results.size)
        assertEquals(setOf("flag1", "flag2", "flag3"), results.map { it.flagName }.toSet())
    }

    @Test
    fun `insert with REPLACE on conflict`() {
        val entity1 = EvaluationEntity("keyHash1", "flag1", "attrsHash1", """{"treatment":"on"}""", System.currentTimeMillis())
        dao.insert(listOf(entity1))

        val entity2 = EvaluationEntity("keyHash1", "flag1", "attrsHash1", """{"treatment":"off"}""", System.currentTimeMillis() + 1000)
        dao.insert(listOf(entity2))

        val results = dao.getByKeyAndAttrs("keyHash1", "attrsHash1")
        assertEquals(1, results.size)
        assertEquals("""{"treatment":"off"}""", results[0].body)
    }

    @Test
    fun `getByKeyAndAttrs returns empty list when no match`() {
        val results = dao.getByKeyAndAttrs("nonexistent", "noattrs")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `getByKeyAndAttrs only returns entities for matching keyHash and attrsHash`() {
        dao.insert(listOf(
            EvaluationEntity("keyHash1", "flag1", "attrsHash1", """{"treatment":"on"}""", System.currentTimeMillis()),
            EvaluationEntity("keyHash1", "flag1", "attrsHash2", """{"treatment":"off"}""", System.currentTimeMillis()),
            EvaluationEntity("keyHash2", "flag1", "attrsHash1", """{"treatment":"control"}""", System.currentTimeMillis())
        ))

        val results = dao.getByKeyAndAttrs("keyHash1", "attrsHash1")
        assertEquals(1, results.size)
        assertEquals("keyHash1", results[0].keyHash)
        assertEquals("attrsHash1", results[0].attrsHash)
    }

    @Test
    fun `deleteByKeyAndAttrs removes only matching rows`() {
        dao.insert(listOf(
            EvaluationEntity("keyHash1", "flag1", "attrsHash1", """{"treatment":"on"}""", System.currentTimeMillis()),
            EvaluationEntity("keyHash1", "flag2", "attrsHash1", """{"treatment":"off"}""", System.currentTimeMillis()),
            EvaluationEntity("keyHash1", "flag1", "attrsHash2", """{"treatment":"control"}""", System.currentTimeMillis())
        ))

        dao.deleteByKeyAndAttrs("keyHash1", "attrsHash1")

        assertTrue(dao.getByKeyAndAttrs("keyHash1", "attrsHash1").isEmpty())
        assertEquals(1, dao.getByKeyAndAttrs("keyHash1", "attrsHash2").size)
    }

    @Test
    fun `deleteByKeyHash removes all rows for keyHash regardless of attrsHash`() {
        dao.insert(listOf(
            EvaluationEntity("keyHash1", "flag1", "attrsHash1", """{"treatment":"on"}""", System.currentTimeMillis()),
            EvaluationEntity("keyHash1", "flag1", "attrsHash2", """{"treatment":"off"}""", System.currentTimeMillis()),
            EvaluationEntity("keyHash2", "flag1", "attrsHash1", """{"treatment":"control"}""", System.currentTimeMillis())
        ))

        dao.deleteByKeyHash("keyHash1")

        assertTrue(dao.getByKeyAndAttrs("keyHash1", "attrsHash1").isEmpty())
        assertTrue(dao.getByKeyAndAttrs("keyHash1", "attrsHash2").isEmpty())
        assertEquals(1, dao.getByKeyAndAttrs("keyHash2", "attrsHash1").size)
    }

    @Test
    fun `replaceForKeyAndAttrs is atomic transaction`() {
        dao.insert(listOf(
            EvaluationEntity("keyHash1", "flag1", "attrsHash1", """{"treatment":"on"}""", System.currentTimeMillis()),
            EvaluationEntity("keyHash1", "flag2", "attrsHash1", """{"treatment":"off"}""", System.currentTimeMillis())
        ))

        val newEntities = listOf(
            EvaluationEntity("keyHash1", "flag3", "attrsHash1", """{"treatment":"control"}""", System.currentTimeMillis()),
            EvaluationEntity("keyHash1", "flag4", "attrsHash1", """{"treatment":"on"}""", System.currentTimeMillis())
        )

        dao.replaceForKeyAndAttrs("keyHash1", "attrsHash1", newEntities)

        val results = dao.getByKeyAndAttrs("keyHash1", "attrsHash1")
        assertEquals(2, results.size)
        assertEquals(setOf("flag3", "flag4"), results.map { it.flagName }.toSet())
    }
}
