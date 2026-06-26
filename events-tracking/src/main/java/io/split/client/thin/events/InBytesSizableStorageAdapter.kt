package io.split.client.thin.events

import io.split.android.client.submitter.InBytesSizable
import io.split.android.client.submitter.StoragePusher
import io.split.android.client.tracker.TrackerEvent

/**
 * Adapter that allows pushing InBytesSizable wrappers while delegating to a StoragePusher.
 */
class InBytesSizableStorageAdapter(
    private val storagePusher: StoragePusher<TrackerEvent>
) : StoragePusher<InBytesSizable> {

    override fun push(element: InBytesSizable) {
        // EventsPushHandler.TrackerEventWrapper wraps the actual TrackerEvent
        // We need to extract it before pushing to storage
        if (element is EventsPushHandler.TrackerEventAccessor) {
            storagePusher.push(element.getTrackerEvent())
        }
    }
}
