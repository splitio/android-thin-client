package io.split.client.thin.events

import io.split.android.client.submitter.InBytesSizable
import io.split.android.client.submitter.RecorderSyncHelper
import io.split.android.client.tracker.TrackerEvent
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class EventsPushHandlerTest {

    private lateinit var syncHelper: RecorderSyncHelper<InBytesSizable>
    private lateinit var coordinator: EventSubmissionCoordinator
    private lateinit var handler: EventsPushHandler
    private var triggeredReason: EventFlushReason? = null

    @Before
    fun setUp() {
        syncHelper = mock<RecorderSyncHelper<InBytesSizable>>()
        coordinator = mock<EventSubmissionCoordinator>()
        triggeredReason = null

        whenever(coordinator.triggerSubmission(any())).then {
            triggeredReason = it.getArgument(0)
            null
        }

        handler = EventsPushHandler(syncHelper, coordinator)
    }

    @Test
    fun `accept pushes to sync helper`() {
        val event = createEvent(sizeInBytes = 100)
        whenever(syncHelper.pushAndCheckIfFlushNeeded(any())).thenReturn(false)

        handler.accept(event)

        verify(syncHelper).pushAndCheckIfFlushNeeded(any())
    }

    @Test
    fun `accept triggers submission when flush needed`() {
        val event = createEvent()
        whenever(syncHelper.pushAndCheckIfFlushNeeded(any())).thenReturn(true)

        handler.accept(event)

        verify(coordinator).triggerSubmission(EventFlushReason.QUEUE)
        assertEquals(EventFlushReason.QUEUE, triggeredReason)
    }

    @Test
    fun `accept does not trigger when flush not needed`() {
        val event = createEvent()
        whenever(syncHelper.pushAndCheckIfFlushNeeded(any())).thenReturn(false)

        handler.accept(event)

        verify(coordinator, never()).triggerSubmission(any())
    }

    @Test
    fun `wraps TrackerEvent with correct size`() {
        val event = createEvent(sizeInBytes = 250)
        var capturedSizable: InBytesSizable? = null

        whenever(syncHelper.pushAndCheckIfFlushNeeded(any())).then {
            capturedSizable = it.getArgument(0)
            false
        }

        handler.accept(event)

        assertEquals(250L, capturedSizable?.sizeInBytes)
    }

    private fun createEvent(sizeInBytes: Int = 100): TrackerEvent {
        return TrackerEvent().apply {
            this.eventType = "test-event"
            this.key = "test-key"
            this.trafficType = "user"
            this.value = 0.0
            this.timestamp = System.currentTimeMillis()
            this.properties = emptyMap()
            this.sizeInBytes = sizeInBytes
        }
    }
}
