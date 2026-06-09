package io.split.client.thin.internal.streaming

import io.split.android.client.streaming.support.CompressionType

enum class EvaluationUpdateStrategy(val code: Int) {
    UNBOUNDED_FETCH_REQUEST(0),
    BOUNDED_FETCH_REQUEST(1);

    companion object {
        fun from(code: Int?): EvaluationUpdateStrategy? = when (code) {
            null, 0 -> UNBOUNDED_FETCH_REQUEST
            1 -> BOUNDED_FETCH_REQUEST
            else -> null
        }
    }
}

sealed class ThinNotification(
    val type: ThinNotificationType,
    val channel: String?,
    val timestamp: Long
)

data class EvaluationUpdateNotification(
    val changeNumber: Long,
    val channelName: String?,
    val eventTimestamp: Long,
    val updateIntervalMs: Long? = null,
    val algorithmSeed: Int? = null,
    val hashingAlgorithm: Int? = null,
    val updateStrategy: EvaluationUpdateStrategy? = EvaluationUpdateStrategy.UNBOUNDED_FETCH_REQUEST,
    val data: String? = null,
    val compression: CompressionType = CompressionType.NONE,
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
