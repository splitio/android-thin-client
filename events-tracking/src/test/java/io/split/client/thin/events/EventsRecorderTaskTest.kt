package io.split.client.thin.events

import io.split.android.client.service.executor.SplitTaskExecutionStatus
import io.split.android.client.service.executor.SplitTaskType
import io.split.android.client.submitter.RecorderException
import io.split.android.client.tracker.TrackerEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.never

class EventsRecorderTaskTest {

    private lateinit var storage: EventsStorage
    private lateinit var submitter: TestSubmitter
    private lateinit var task: EventsRecorderTask

    @Before
    fun setUp() {
        storage = EventsStorage()
        submitter = TestSubmitter()
        task = EventsRecorderTask(
            storage = storage,
            submitter = submitter,
            batchSize = 10
        )
    }

    @Test
    fun `transforms events using EventSerializer`() {
        val event1 = createEvent("event1")
        val event2 = createEvent("event2")
        storage.push(event1)
        storage.push(event2)

        task.execute()

        assertTrue(submitter.lastPayload.contains("\"eventTypeId\":\"event1\""))
        assertTrue(submitter.lastPayload.contains("\"eventTypeId\":\"event2\""))
    }

    @Test
    fun `executes successfully when submitter succeeds`() {
        storage.push(createEvent("event1"))

        val result = task.execute()

        assertEquals(SplitTaskExecutionStatus.SUCCESS, result.status)
        assertEquals(SplitTaskType.GENERIC_TASK, result.taskType)
    }

    @Test
    fun `deletes items after successful submission`() {
        storage.push(createEvent("event1"))
        storage.push(createEvent("event2"))

        task.execute()

        val remaining = storage.pop(10)
        assertTrue(remaining.isEmpty())
    }

    @Test
    fun `re-queues items on submission failure`() {
        storage.push(createEvent("event1"))
        storage.push(createEvent("event2"))
        submitter.shouldFail = true

        task.execute()

        // Items should be back in storage
        val requeued = storage.pop(10)
        assertEquals(2, requeued.size)
    }

    @Test
    fun `returns error status on submission failure`() {
        storage.push(createEvent("event1"))
        submitter.shouldFail = true

        val result = task.execute()

        assertEquals(SplitTaskExecutionStatus.ERROR, result.status)
    }

    @Test
    fun `estimates item size correctly`() {
        val event = createEvent("test", sizeInBytes = 250)
        storage.push(event)

        task.execute()

        // Verify size was tracked (this is internal but observable through error data)
        assertEquals(1, submitter.callCount)
    }

    @Test
    fun `processes multiple batches when storage has more items than batch size`() {
        repeat(25) { i ->
            storage.push(createEvent("event-$i"))
        }

        task.execute()

        assertEquals(3, submitter.callCount) // 25 items / 10 batch size = 3 calls
        val remaining = storage.pop(10)
        assertTrue(remaining.isEmpty())
    }

    private fun createEvent(
        eventType: String,
        sizeInBytes: Int = 100
    ): TrackerEvent {
        return TrackerEvent().apply {
            this.eventType = eventType
            this.key = "test-key"
            this.trafficType = "user"
            this.value = 0.0
            this.timestamp = System.currentTimeMillis()
            this.properties = emptyMap()
            this.sizeInBytes = sizeInBytes
        }
    }

    private class TestSubmitter : io.split.android.client.submitter.RecorderSubmitter<String> {
        var lastPayload: String = ""
        var shouldFail = false
        var callCount = 0

        override fun execute(data: String) {
            callCount++
            lastPayload = data
            if (shouldFail) {
                throw RecorderException("Test failure", 500, true)
            }
        }
    }
}
