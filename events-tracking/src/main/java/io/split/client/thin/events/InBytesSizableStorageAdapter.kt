package io.split.client.thin.events

import io.split.android.client.submitter.InBytesSizable
import io.split.android.client.submitter.StoragePusher

/**
 * Adapter that allows pushing InBytesSizable wrappers while delegating to EventsStorage.
 */
class InBytesSizableStorageAdapter(
    private val eventsStorage: EventsStorage
) : StoragePusher<InBytesSizable> {

    override fun push(element: InBytesSizable) {
        // EventsPushHandler.TrackerEventWrapper wraps the actual TrackerEvent
        // We need to extract it before pushing to storage
        if (element is EventsPushHandler.TrackerEventAccessor) {
            eventsStorage.push(element.getTrackerEvent())
        }
    }
}
