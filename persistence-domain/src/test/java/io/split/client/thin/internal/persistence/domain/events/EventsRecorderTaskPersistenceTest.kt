package io.split.client.thin.internal.persistence.domain.events

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.split.android.client.submitter.RecorderException
import io.split.android.client.tracker.TrackerEvent
import io.split.client.thin.events.EventsRecorderTask
import io.split.client.thin.internal.persistence.RoomEventsPersistence
import io.split.client.thin.internal.persistence.ThinClientDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EventsRecorderTaskPersistenceTest {

    private lateinit var database: ThinClientDatabase
    private lateinit var roomPersistence: RoomEventsPersistence
    private lateinit var domainStorage: PersistentEventsStorage
    private val serializer = TrackerEventSerializer()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ThinClientDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        roomPersistence = RoomEventsPersistence(database.eventDao())
        domainStorage = PersistentEventsStorage(
            roomEventsPersistence = roomPersistence,
            serializer = serializer,
            callbacks = mock(EventsPersistenceCallbacks::class.java),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `events are not removed from DB when POST fails`() {
        roomPersistence.push(serializer.serialize(createEvent("event1")))
        roomPersistence.push(serializer.serialize(createEvent("event2")))

        val task = EventsRecorderTask(
            storage = domainStorage,
            submitter = { throw RecorderException("network error", 500, true) },
            batchSize = 10
        )
        task.execute()

        assertEquals(2, database.eventDao().count())
    }

    @Test
    fun `events are removed from DB after successful POST`() {
        roomPersistence.push(serializer.serialize(createEvent("event1")))
        roomPersistence.push(serializer.serialize(createEvent("event2")))

        val task = EventsRecorderTask(
            storage = domainStorage,
            submitter = { /* success */ },
            batchSize = 10
        )
        task.execute()

        assertEquals(0, database.eventDao().count())
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
