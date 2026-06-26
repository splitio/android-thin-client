package io.split.client.thin.internal.streaming

/**
 * Tracks per-channel occupancy (number of publishers) for the streaming connection and
 * reports the absolute current state (any publishers present vs. zero).
 *
 * Occupancy notifications are handled on [kotlinx.coroutines.Dispatchers.Default], which
 * dispatches across multiple worker threads, so this state must be safe for concurrent
 * access. All mutating and reading operations are synchronized.
 *
 * On (re)connect the tracker defaults to "present" so does not fall back to polling before any
 * occupancy notification has arrived. Per-channel timestamp dedup ignores out-of-order notifications.
 */
internal class OccupancyTracker {

    private class ChannelInfo(var publishers: Int, var lastTimestamp: Long)

    private val occupancyByChannel = mutableMapOf<String, ChannelInfo>()

    // True until the first occupancy notification is applied: connection is assumed to have
    // publishers present by default.
    private var noOccupancyDataYet = true

    /**
     * Records the publisher count for [channel] at [eventTimestamp]. Notifications with a
     * timestamp older than or equal to the last seen for that channel are ignored.
     *
     * @return true if the notification was applied, false if it was stale or had no channel.
     */
    @Synchronized
    fun update(channel: String?, publishers: Int, eventTimestamp: Long): Boolean {
        if (channel == null) {
            return false
        }
        val existing = occupancyByChannel[channel]
        if (existing != null && eventTimestamp <= existing.lastTimestamp) {
            return false
        }
        if (existing != null) {
            existing.publishers = publishers
            existing.lastTimestamp = eventTimestamp
        } else {
            occupancyByChannel[channel] = ChannelInfo(publishers, eventTimestamp)
        }
        noOccupancyDataYet = false
        return true
    }

    /** Whether the connection currently has zero total publishers across all channels. */
    @Synchronized
    fun isZero(): Boolean {
        if (noOccupancyDataYet) {
            return false
        }
        return occupancyByChannel.values.sumOf { it.publishers } == 0
    }

    /** Clears all channel occupancy and resets to "present by default" for a fresh connection. */
    @Synchronized
    fun reset() {
        occupancyByChannel.clear()
        noOccupancyDataYet = true
    }
}
