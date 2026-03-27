package io.split.client.thin.internal.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.split.client.thin.EvaluationResult
import io.split.client.thin.internal.evaluation.StoredEvaluation
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
        persistence = RoomEvaluationPersistence(database.evaluationDao())
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
        val evaluations = listOf(
            StoredEvaluation(
                result = EvaluationResult(
                    flag = "flag1",
                    treatment = "on",
                    config = """{"color":"blue"}""",
                    label = "matched",
                    changeNumber = 100L
                ),
                flagSets = setOf("set1", "set2")
            ),
            StoredEvaluation(
                result = EvaluationResult(
                    flag = "flag2",
                    treatment = "off",
                    config = null,
                    label = "default",
                    changeNumber = 100L
                ),
                flagSets = emptySet()
            )
        )

        persistence.persistForKey("user1", 100L, evaluations)

        val loaded = persistence.loadForKey("user1")
        assertEquals(100L, loaded?.changeNumber)
        assertEquals(2, loaded?.evaluations?.size)

        val flag1 = loaded?.evaluations?.find { it.result.flag == "flag1" }
        assertEquals("on", flag1?.result?.treatment)
        assertEquals("""{"color":"blue"}""", flag1?.result?.config)
        assertEquals("matched", flag1?.result?.label)
        assertEquals(setOf("set1", "set2"), flag1?.flagSets)

        val flag2 = loaded?.evaluations?.find { it.result.flag == "flag2" }
        assertEquals("off", flag2?.result?.treatment)
        assertNull(flag2?.result?.config)
        assertEquals(emptySet(), flag2?.flagSets)
    }

    @Test
    fun `persistForKey replaces existing data`() {
        val initial = listOf(
            StoredEvaluation(
                result = EvaluationResult("flag1", "on", null, null, 100L),
                flagSets = emptySet()
            )
        )
        persistence.persistForKey("user1", 100L, initial)

        val updated = listOf(
            StoredEvaluation(
                result = EvaluationResult("flag2", "off", null, null, 200L),
                flagSets = emptySet()
            ),
            StoredEvaluation(
                result = EvaluationResult("flag3", "control", null, null, 200L),
                flagSets = emptySet()
            )
        )
        persistence.persistForKey("user1", 200L, updated)

        val loaded = persistence.loadForKey("user1")
        assertEquals(200L, loaded?.changeNumber)
        assertEquals(2, loaded?.evaluations?.size)
        assertEquals(setOf("flag2", "flag3"), loaded?.evaluations?.map { it.result.flag }?.toSet())
    }

    @Test
    fun `clearForKey removes all data for key`() {
        persistence.persistForKey("user1", 100L, listOf(
            StoredEvaluation(
                result = EvaluationResult("flag1", "on", null, null, 100L),
                flagSets = emptySet()
            )
        ))

        persistence.clearForKey("user1")

        val loaded = persistence.loadForKey("user1")
        assertNull(loaded)
    }

    @Test
    fun `multiple keys are isolated`() {
        val user1Evals = listOf(
            StoredEvaluation(
                result = EvaluationResult("flag1", "on", null, null, 100L),
                flagSets = emptySet()
            )
        )
        val user2Evals = listOf(
            StoredEvaluation(
                result = EvaluationResult("flag2", "off", null, null, 200L),
                flagSets = emptySet()
            )
        )

        persistence.persistForKey("user1", 100L, user1Evals)
        persistence.persistForKey("user2", 200L, user2Evals)

        val loaded1 = persistence.loadForKey("user1")
        assertEquals(100L, loaded1?.changeNumber)
        assertEquals("flag1", loaded1?.evaluations?.first()?.result?.flag)

        val loaded2 = persistence.loadForKey("user2")
        assertEquals(200L, loaded2?.changeNumber)
        assertEquals("flag2", loaded2?.evaluations?.first()?.result?.flag)
    }

    @Test
    fun `empty evaluation list results in null on load`() {
        persistence.persistForKey("user1", 100L, emptyList())

        val loaded = persistence.loadForKey("user1")
        // Can't persist changeNumber without entities due to schema design
        assertNull(loaded)
    }
}
