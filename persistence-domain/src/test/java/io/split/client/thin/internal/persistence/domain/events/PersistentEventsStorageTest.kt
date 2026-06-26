package io.split.client.thin.internal.persistence.domain.events

import io.split.android.client.tracker.TrackerEvent
import io.split.client.thin.internal.persistence.PersistentEventsStorage as RoomEventsPersistence
import io.split.client.thin.internal.persistence.StoredEvent
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
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyList
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
        storage = PersistentEventsStorage(roomStorage, serializer, callbacks)
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
        `when`(roomStorage.pop(2)).thenReturn(listOf(StoredEvent(1L, json1), StoredEvent(2L, json2)))
        `when`(serializer.deserialize(json1)).thenReturn(event1)
        `when`(serializer.deserialize(json2)).thenReturn(event2)

        val result = storage.pop(2)

        assertEquals(listOf(event1, event2), result)
        verify(callbacks).onEventPopped(2)
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
    fun `delete uses IDs from preceding pop`() {
        val json1 = "{\"key\":\"a\"}"
        val json2 = "{\"key\":\"b\"}"
        val event1 = makeEvent("a")
        val event2 = makeEvent("b")
        `when`(roomStorage.pop(2)).thenReturn(listOf(StoredEvent(10L, json1), StoredEvent(20L, json2)))
        `when`(serializer.deserialize(json1)).thenReturn(event1)
        `when`(serializer.deserialize(json2)).thenReturn(event2)

        val popped = storage.pop(2)
        storage.delete(popped)

        verify(roomStorage).delete(listOf(10L, 20L))
    }

    @Test
    fun `delete of unknown items does not call room storage`() {
        val event = makeEvent()

        storage.delete(listOf(event)) // not popped first — no ID in map

        verify(roomStorage, never()).delete(anyList())
    }

    @Test
    fun `delete tracks each popped event independently even with equal content`() {
        val json = "{\"key\":\"a\"}"
        val event1 = makeEvent("a")
        val event2 = makeEvent("a") // same content, different instance
        `when`(roomStorage.pop(1))
            .thenReturn(listOf(StoredEvent(10L, json)))
            .thenReturn(listOf(StoredEvent(20L, json)))
        `when`(serializer.deserialize(json)).thenReturn(event1).thenReturn(event2)

        val popped1 = storage.pop(1)
        val popped2 = storage.pop(1)
        storage.delete(popped1 + popped2)

        verify(roomStorage).delete(listOf(10L, 20L))
    }

    @Test
    fun `push persists event synchronously without coroutine advancement`() {
        val event = makeEvent()
        `when`(serializer.serialize(event)).thenReturn("{\"key\":\"user1\"}")

        storage.push(event) // must complete synchronously — no advanceUntilIdle()

        verify(serializer).serialize(event)
        verify(roomStorage).push("{\"key\":\"user1\"}")
        verify(callbacks).onEventPushed()
    }

    @Test
    fun `setActive is a no-op`() = scope.runTest {
        val event = makeEvent()

        storage.setActive(listOf(event))
        advanceUntilIdle()

        verify(roomStorage, never()).push(anyString())
        verify(roomStorage, never()).delete(anyList())
    }

    @Test
    fun `pop does not call onEventPopped when queue is empty`() {
        `when`(roomStorage.pop(anyInt())).thenReturn(emptyList())

        storage.pop(5)

        verify(callbacks, never()).onEventPopped(anyInt())
    }
}
