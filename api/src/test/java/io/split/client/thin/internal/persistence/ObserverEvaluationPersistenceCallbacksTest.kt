package io.split.client.thin.internal.persistence

import io.split.client.thin.Key
import io.split.client.thin.internal.evaluation.EvaluationKey
import io.split.client.thin.internal.evaluation.StoredEvaluation
import io.split.client.thin.internal.observer.CompositeObserver
import io.split.client.thin.internal.observer.ObservableEvent
import io.split.client.thin.internal.observer.ObservableEventType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.ArgumentCaptor

class ObserverEvaluationPersistenceCallbacksTest {

    private lateinit var compositeObserver: CompositeObserver
    private lateinit var callbacks: ObserverEvaluationPersistenceCallbacks

    @Before
    fun setUp() {
        compositeObserver = mock(CompositeObserver::class.java)
        callbacks = ObserverEvaluationPersistenceCallbacks(compositeObserver)
    }

    private fun captureEvent(): ObservableEvent {
        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(ObservableEvent::class.java)
        verify(compositeObserver).notifyEvent(captor.capture() ?: ObservableEvent(""))
        return captor.value
    }

    @Test
    fun `onEvalStorageUpdated dispatches EVAL_STORAGE_UPDATED event`() {
        val evalKey = EvaluationKey(Key("user-1", "user-1"))
        val evaluations = emptyList<StoredEvaluation>()

        callbacks.onEvalStorageUpdated(evalKey, 123L, evaluations)

        assertEquals(ObservableEventType.EVAL_STORAGE_UPDATED, captureEvent().type)
    }

    @Test
    fun `onLoadStarted dispatches EVAL_STORAGE_LOAD_STARTED event`() {
        callbacks.onLoadStarted()

        assertEquals(ObservableEventType.EVAL_STORAGE_LOAD_STARTED, captureEvent().type)
    }

    @Test
    fun `onLoadSucceeded dispatches EVAL_STORAGE_LOAD_SUCCEEDED event`() {
        callbacks.onLoadSucceeded(1000L)

        assertEquals(ObservableEventType.EVAL_STORAGE_LOAD_SUCCEEDED, captureEvent().type)
    }

    @Test
    fun `onLoadFailed dispatches EVAL_STORAGE_LOAD_FAILED event`() {
        callbacks.onLoadFailed("some error")

        assertEquals(ObservableEventType.EVAL_STORAGE_LOAD_FAILED, captureEvent().type)
    }

    @Test
    fun `onWriteScheduled dispatches EVAL_STORAGE_WRITE_SCHEDULED event`() {
        callbacks.onWriteScheduled()

        assertEquals(ObservableEventType.EVAL_STORAGE_WRITE_SCHEDULED, captureEvent().type)
    }

    @Test
    fun `onWriteSucceeded dispatches EVAL_STORAGE_WRITE_SUCCEEDED event`() {
        callbacks.onWriteSucceeded()

        assertEquals(ObservableEventType.EVAL_STORAGE_WRITE_SUCCEEDED, captureEvent().type)
    }

    @Test
    fun `onWriteFailed dispatches EVAL_STORAGE_WRITE_FAILED event`() {
        callbacks.onWriteFailed("disk full")

        assertEquals(ObservableEventType.EVAL_STORAGE_WRITE_FAILED, captureEvent().type)
    }
}
