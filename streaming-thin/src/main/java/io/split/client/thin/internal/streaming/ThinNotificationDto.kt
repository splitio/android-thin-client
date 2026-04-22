package io.split.client.thin.internal.streaming

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Outer envelope DTO
@Serializable
internal data class RawThinNotificationDto(
    @SerialName("channel") val channel: String? = null,
    @SerialName("data") val data: String,  // JSON string to parse separately
    @SerialName("timestamp") val timestamp: Long
)

// Discriminator DTO — parse once to determine the notification type
@Serializable
internal data class NotificationTypeDto(
    @SerialName("type") val type: String? = null
)

// Inner data DTOs (one per notification type)
@Serializable
internal data class EvaluationUpdateDataDto(
    @SerialName("type") val type: String,
    @SerialName("changeNumber") val changeNumber: Long,
    @SerialName("i") val updateIntervalMs: Long? = null,
    @SerialName("s") val algorithmSeed: Int? = null,
    @SerialName("h") val hashingAlgorithm: Int? = null
)

@Serializable
internal data class ControlDataDto(
    @SerialName("type") val type: String,
    @SerialName("controlType") val controlType: String
)

@Serializable
internal data class OccupancyDataDto(
    @SerialName("metrics") val metrics: OccupancyMetricsDto
)

@Serializable
internal data class OccupancyMetricsDto(
    @SerialName("publishers") val publishers: Int
)

@Serializable
internal data class ErrorDataDto(
    @SerialName("type") val type: String,
    @SerialName("message") val message: String? = null,
    @SerialName("code") val code: Int? = null,
    @SerialName("statusCode") val statusCode: Int? = null
)
