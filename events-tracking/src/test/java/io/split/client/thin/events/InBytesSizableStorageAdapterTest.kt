package io.split.client.thin.events

import io.split.android.client.submitter.InBytesSizable
import io.split.android.client.submitter.StoragePusher
import io.split.android.client.tracker.TrackerEvent
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock

class InBytesSizableStorageAdapterTest {

    private lateinit var storagePusher: StoragePusher<TrackerEvent>
    private lateinit var adapter: InBytesSizableStorageAdapter

    @Before
    fun setUp() {
        storagePusher = mock()
        adapter = InBytesSizableStorageAdapter(storagePusher)
    }

    private inner class FakeAccessor(private val event: TrackerEvent) :
        InBytesSizable, EventsPushHandler.TrackerEventAccessor {
        override fun getSizeInBytes() = 0L
        override fun getTrackerEvent() = event
    }

    @Test
    fun `push delegates TrackerEventAccessor element to storagePusher`() {
        val trackerEvent = mock<TrackerEvent>()
        val accessor = FakeAccessor(trackerEvent)

        adapter.push(accessor)

        verify(storagePusher).push(trackerEvent)
    }

    @Test
    fun `push ignores non-TrackerEventAccessor elements`() {
        val nonAccessor = mock<InBytesSizable>()

        adapter.push(nonAccessor)

        verify(storagePusher, never()).push(any())
    }
}
