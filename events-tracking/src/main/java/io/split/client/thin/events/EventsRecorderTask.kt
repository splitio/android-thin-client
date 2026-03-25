package io.split.client.thin.events

import io.split.android.client.service.executor.SplitTaskType
import io.split.android.client.submitter.RecorderStorage
import io.split.android.client.submitter.RecorderSubmitter
import io.split.android.client.submitter.RecorderTask
import io.split.android.client.tracker.TrackerEvent

class EventsRecorderTask(
    storage: RecorderStorage<TrackerEvent>,
    submitter: RecorderSubmitter<String>,
    batchSize: Int
) : RecorderTask<TrackerEvent, String>(
    storage,
    submitter,
    batchSize,
    SplitTaskType.GENERIC_TASK,
    null, // No telemetry
    0     // No chunking
) {
    override fun transformForSubmission(items: List<TrackerEvent>): String {
        return EventSerializer.serialize(items)
    }

    override fun estimateItemSize(item: TrackerEvent): Long {
        return item.sizeInBytes.toLong()
    }
}
