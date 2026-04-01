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
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoomEvaluationPersistenceTest {

    private lateinit var database: ThinClientDatabase
    private lateinit var persistence: RoomEvaluationPersistence

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ThinClientDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        persistence = RoomEvaluationPersistence(database)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `loadForKey returns null when no data exists`() {
        val data = persistence.loadForKey("nonexistent")
        assertNull(data)
    }

    @Test
    fun `persistForKey and loadForKey round trip`() {
        val flag1Json = """{"result":{"flag":"flag1","treatment":"on","config":"{\"color\":\"blue\"}","label":"matched","changeNumber":100},"flagSets":["set1","set2"]}"""
        val flag2Json = """{"result":{"flag":"flag2","treatment":"off","config":null,"label":"default","changeNumber":100},"flagSets":[]}"""

        val evaluations = listOf(
            SerializedEvaluation("flag1", flag1Json),
            SerializedEvaluation("flag2", flag2Json)
        )

        persistence.persistForKey("user1", 100L, evaluations)

        val loaded = persistence.loadForKey("user1")
        assertEquals(100L, loaded?.changeNumber)
        assertEquals(2, loaded?.evaluations?.size)
        assertTrue(loaded?.evaluations?.contains(flag1Json) == true)
        assertTrue(loaded?.evaluations?.contains(flag2Json) == true)
    }

    @Test
    fun `persistForKey replaces existing data`() {
        val flag1Json = """{"result":{"flag":"flag1","treatment":"on","config":null,"label":null,"changeNumber":100},"flagSets":[]}"""
        val initial = listOf(SerializedEvaluation("flag1", flag1Json))
        persistence.persistForKey("user1", 100L, initial)

        val flag2Json = """{"result":{"flag":"flag2","treatment":"off","config":null,"label":null,"changeNumber":200},"flagSets":[]}"""
        val flag3Json = """{"result":{"flag":"flag3","treatment":"control","config":null,"label":null,"changeNumber":200},"flagSets":[]}"""
        val updated = listOf(
            SerializedEvaluation("flag2", flag2Json),
            SerializedEvaluation("flag3", flag3Json)
        )
        persistence.persistForKey("user1", 200L, updated)

        val loaded = persistence.loadForKey("user1")
        assertEquals(200L, loaded?.changeNumber)
        assertEquals(2, loaded?.evaluations?.size)
        assertTrue(loaded?.evaluations?.contains(flag2Json) == true)
        assertTrue(loaded?.evaluations?.contains(flag3Json) == true)
    }

    @Test
    fun `clearForKey removes all data for key`() {
        val flag1Json = """{"result":{"flag":"flag1","treatment":"on","config":null,"label":null,"changeNumber":100},"flagSets":[]}"""
        persistence.persistForKey("user1", 100L, listOf(SerializedEvaluation("flag1", flag1Json)))

        persistence.clearForKey("user1")

        val loaded = persistence.loadForKey("user1")
        assertNull(loaded)
    }

    @Test
    fun `multiple keys are isolated`() {
        val flag1Json = """{"result":{"flag":"flag1","treatment":"on","config":null,"label":null,"changeNumber":100},"flagSets":[]}"""
        val flag2Json = """{"result":{"flag":"flag2","treatment":"off","config":null,"label":null,"changeNumber":200},"flagSets":[]}"""

        val user1Evals = listOf(SerializedEvaluation("flag1", flag1Json))
        val user2Evals = listOf(SerializedEvaluation("flag2", flag2Json))

        persistence.persistForKey("user1", 100L, user1Evals)
        persistence.persistForKey("user2", 200L, user2Evals)

        val loaded1 = persistence.loadForKey("user1")
        assertEquals(100L, loaded1?.changeNumber)
        assertTrue(loaded1?.evaluations?.contains(flag1Json) == true)

        val loaded2 = persistence.loadForKey("user2")
        assertEquals(200L, loaded2?.changeNumber)
        assertTrue(loaded2?.evaluations?.contains(flag2Json) == true)
    }

    @Test
    fun `empty evaluation list clears both metadata and evaluations`() {
        val flag1Json = """{"result":{"flag":"flag1","treatment":"on","config":null,"label":null,"changeNumber":100},"flagSets":[]}"""
        persistence.persistForKey("user1", 100L, listOf(SerializedEvaluation("flag1", flag1Json)))

        persistence.persistForKey("user1", 200L, emptyList())

        val loaded = persistence.loadForKey("user1")
        assertNull(loaded)
    }

    @Test
    fun `changeNumber is stored once per matchingKey`() {
        val flag1Json = """{"result":{"flag":"flag1","treatment":"on","config":null,"label":null,"changeNumber":100},"flagSets":[]}"""
        val flag2Json = """{"result":{"flag":"flag2","treatment":"off","config":null,"label":null,"changeNumber":100},"flagSets":[]}"""

        persistence.persistForKey("user1", 12345L, listOf(
            SerializedEvaluation("flag1", flag1Json),
            SerializedEvaluation("flag2", flag2Json)
        ))

        val info = database.generalInfoDao().getByKey("user1")
        val changeNumber = org.json.JSONObject(info!!.value).getLong("changeNumber")
        assertEquals(12345L, changeNumber)

        val evaluationEntities = database.evaluationDao().getByKey("user1")
        assertEquals(2, evaluationEntities.size)
    }
}
