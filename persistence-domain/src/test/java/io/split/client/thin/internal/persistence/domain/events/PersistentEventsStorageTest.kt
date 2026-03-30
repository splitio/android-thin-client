package io.split.client.thin.internal.persistence.domain.events

import io.split.android.client.tracker.TrackerEvent
import io.split.client.thin.internal.persistence.PersistentEventsStorage as RoomEventsPersistence
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString

@OptIn(ExperimentalCoroutinesApi::class)
class PersistentEventsStorageTest {

    private lateinit var roomStorage: RoomEventsPersistence
    private lateinit var serializer: TrackerEventSerializer
    private lateinit var callbacks: EventsPersistenceCallbacks
    private lateinit var scope: TestScope
    private lateinit var storage: PersistentEventsStorage

    private fun makeEvent(key: String = "user1"): TrackerEvent {
        return TrackerEvent().apply {
            trafficType = "user"
            eventType = "purchase"
            this.key = key
            value = 1.0
            timestamp = 1000L
        }
    }

    @Before
    fun setUp() {
        roomStorage = mock(RoomEventsPersistence::class.java)
        serializer = mock(TrackerEventSerializer::class.java)
        callbacks = mock(EventsPersistenceCallbacks::class.java)
        scope = TestScope()
        storage = PersistentEventsStorage(roomStorage, serializer, callbacks, scope)
    }

    @Test
    fun `push serializes event and persists it, then calls onEventPushed`() = scope.runTest {
        val event = makeEvent()
        `when`(serializer.serialize(event)).thenReturn("{\"key\":\"user1\"}")

        storage.push(event)
        advanceUntilIdle()

        verify(serializer).serialize(event)
        verify(roomStorage).push("{\"key\":\"user1\"}")
        verify(callbacks).onEventPushed()
    }

    @Test
    fun `push calls onPersistenceFailed on exception`() = scope.runTest {
        val event = makeEvent()
        `when`(serializer.serialize(event)).thenThrow(RuntimeException("serialize error"))

        storage.push(event)
        advanceUntilIdle()

        verify(callbacks).onPersistenceFailed("serialize error")
        verify(roomStorage, never()).push(anyString())
        verify(callbacks, never()).onEventPushed()
    }

    @Test
    fun `pop deserializes items and calls onEventPopped`() {
        val json1 = "{\"key\":\"a\"}"
        val json2 = "{\"key\":\"b\"}"
        val event1 = makeEvent("a")
        val event2 = makeEvent("b")
        `when`(roomStorage.pop(2)).thenReturn(listOf(json1, json2))
        `when`(serializer.deserialize(json1)).thenReturn(event1)
        `when`(serializer.deserialize(json2)).thenReturn(event2)

        val result = storage.pop(2)

        assertEquals(listOf(event1, event2), result)
        verify(callbacks).onEventPopped(2) // verify actual count (2 items returned)
    }

    @Test
    fun `pop returns emptyList on exception and calls onPersistenceFailed`() {
        `when`(roomStorage.pop(anyInt())).thenThrow(RuntimeException("pop error"))

        val result = storage.pop(5)

        assertTrue(result.isEmpty())
        verify(callbacks).onPersistenceFailed("pop error")
        verify(callbacks, never()).onEventPopped(anyInt())
    }

    @Test
    fun `delete is a no-op`() {
        val event = makeEvent()
        storage.delete(listOf(event))

        verify(roomStorage, never()).pop(anyInt())
        verify(roomStorage, never()).push(anyString())
    }

    @Test
    fun `setActive re-pushes each item`() = scope.runTest {
        val event1 = makeEvent("a")
        val event2 = makeEvent("b")
        `when`(serializer.serialize(event1)).thenReturn("{\"key\":\"a\"}")
        `when`(serializer.serialize(event2)).thenReturn("{\"key\":\"b\"}")

        storage.setActive(listOf(event1, event2))
        advanceUntilIdle()

        verify(roomStorage).push("{\"key\":\"a\"}")
        verify(roomStorage).push("{\"key\":\"b\"}")
    }
}
