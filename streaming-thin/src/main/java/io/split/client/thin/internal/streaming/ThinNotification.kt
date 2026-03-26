package io.split.client.thin.internal.streaming

internal sealed class ThinNotification(
    val type: ThinNotificationType,
    val channel: String?,
    val timestamp: Long
)

internal data class EvaluationUpdateNotification(
    val changeNumber: Long,
    val channelName: String?,
    val eventTimestamp: Long
) : ThinNotification(ThinNotificationType.EVALUATION_UPDATE, channelName, eventTimestamp)

internal data class ThinControlNotification(
    val controlType: ControlType,
    val channelName: String?,
    val eventTimestamp: Long
) : ThinNotification(ThinNotificationType.CONTROL, channelName, eventTimestamp) {

    enum class ControlType {
        STREAMING_RESUMED,
        STREAMING_DISABLED,
        STREAMING_PAUSED,
        STREAMING_RESET
    }
}

internal data class ThinOccupancyNotification(
    val publishers: Int,
    val channelName: String?,
    val eventTimestamp: Long
) : ThinNotification(ThinNotificationType.OCCUPANCY, channelName, eventTimestamp)

internal data class ThinStreamingError(
    val message: String,
    val code: Int,
    val statusCode: Int?,
    val eventTimestamp: Long
) : ThinNotification(ThinNotificationType.ERROR, null, eventTimestamp)

// Helper for parser
internal data class RawThinNotification(
    val channel: String?,
    val data: String,
    val timestamp: Long
)
