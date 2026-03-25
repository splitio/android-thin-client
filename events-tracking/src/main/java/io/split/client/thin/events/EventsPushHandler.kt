package io.split.client.thin.events

import io.split.android.client.submitter.InBytesSizable
import io.split.android.client.submitter.RecorderSyncHelper
import io.split.android.client.tracker.DefaultTracker
import io.split.android.client.tracker.TrackerEvent

class EventsPushHandler(
    private val syncHelper: RecorderSyncHelper<InBytesSizable>,
    private val coordinator: EventSubmissionCoordinator
) : DefaultTracker.OnEventPush {

    override fun accept(event: TrackerEvent) {
        val sizable = TrackerEventWrapper(event)
        val flushNeeded = syncHelper.pushAndCheckIfFlushNeeded(sizable)

        if (flushNeeded) {
            coordinator.triggerSubmission(EventFlushReason.QUEUE)
        }
    }

    fun interface TrackerEventAccessor {
        fun getTrackerEvent(): TrackerEvent
    }

    private class TrackerEventWrapper(private val event: TrackerEvent) : InBytesSizable, TrackerEventAccessor {
        override fun getSizeInBytes(): Long {
            return event.sizeInBytes.toLong()
        }

        override fun getTrackerEvent(): TrackerEvent {
            return event
        }
    }
}
