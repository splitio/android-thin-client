package io.split.client.thin.internal.streaming

import io.split.android.client.streaming.support.CompressionType
import io.split.android.client.utils.logger.Logger
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal class ThinNotificationParser(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    fun parseRaw(jsonData: String): RawThinNotification? {
        return try {
            val dto = json.decodeFromString<RawThinNotificationDto>(jsonData)
            RawThinNotification(
                channel = dto.channel,
                data = dto.data,
                timestamp = dto.timestamp
            )
        } catch (e: SerializationException) {
            Logger.e("Failed to parse raw notification: ${e.message}")
            null
        }
    }

    fun parse(raw: RawThinNotification): ThinNotification? {
        return try {
            val typeDto = json.decodeFromString<NotificationTypeDto>(raw.data)
            when (typeDto.type) {
                "EVALUATIONS_UPDATE" -> parseEvaluationUpdate(raw)
                "CONTROL" -> parseControl(raw)
                "ERROR" -> parseError(raw)
                null -> parseOccupancy(raw)
                else -> {
                    Logger.w("Unknown notification type: ${typeDto.type}")
                    null
                }
            }
        } catch (e: Exception) {
            Logger.e("Failed to parse notification: ${e.message}")
            null
        }
    }

    private fun parseEvaluationUpdate(raw: RawThinNotification): EvaluationUpdateNotification? {
        return try {
            val dto = json.decodeFromString<EvaluationUpdateDataDto>(raw.data)
            val compression = when (dto.compression) {
                1 -> CompressionType.GZIP
                2 -> CompressionType.ZLIB
                else -> CompressionType.NONE
            }
            EvaluationUpdateNotification(
                changeNumber = dto.changeNumber,
                channelName = raw.channel,
                eventTimestamp = raw.timestamp,
                updateIntervalMs = dto.updateIntervalMs,
                algorithmSeed = dto.algorithmSeed,
                hashingAlgorithm = dto.hashingAlgorithm,
                updateStrategy = EvaluationUpdateStrategy.from(dto.updateStrategy),
                data = dto.data,
                compression = compression,
            )
        } catch (e: SerializationException) {
            Logger.e("Failed to parse EVALUATION_UPDATE: ${e.message}")
            null
        }
    }

    private fun parseControl(raw: RawThinNotification): ThinControlNotification? {
        return try {
            val dto = json.decodeFromString<ControlDataDto>(raw.data)
            val controlType = when (dto.controlType) {
                "STREAMING_RESUMED" -> ThinControlNotification.ControlType.STREAMING_RESUMED
                "STREAMING_DISABLED" -> ThinControlNotification.ControlType.STREAMING_DISABLED
                "STREAMING_PAUSED" -> ThinControlNotification.ControlType.STREAMING_PAUSED
                "STREAMING_RESET" -> ThinControlNotification.ControlType.STREAMING_RESET
                else -> {
                    Logger.w("Unknown control type: ${dto.controlType}")
                    return null
                }
            }
            ThinControlNotification(controlType, raw.channel, raw.timestamp)
        } catch (e: SerializationException) {
            Logger.e("Failed to parse CONTROL: ${e.message}")
            null
        }
    }

    private fun parseOccupancy(raw: RawThinNotification): ThinOccupancyNotification? {
        return try {
            val dto = json.decodeFromString<OccupancyDataDto>(raw.data)
            ThinOccupancyNotification(dto.metrics.publishers, raw.channel, raw.timestamp)
        } catch (e: SerializationException) {
            Logger.e("Failed to parse OCCUPANCY: ${e.message}")
            null
        }
    }

    /**
     * Parses a bare Ably error frame delivered as an `event: error` SSE message (no envelope,
     * no inner `type`), e.g. {"code":40142,"statusCode":401,"message":"Token expired"}.
     */
    fun parseErrorFrame(jsonData: String?): ThinStreamingError? {
        if (jsonData == null) {
            return null
        }
        return try {
            val dto = json.decodeFromString<StreamingErrorFrameDto>(jsonData)
            ThinStreamingError(
                message = dto.message ?: "Unknown error",
                code = dto.code ?: -1,
                statusCode = dto.statusCode,
                eventTimestamp = 0L,
            )
        } catch (e: SerializationException) {
            Logger.e("Failed to parse error frame: ${e.message}")
            null
        }
    }

    private fun parseError(raw: RawThinNotification): ThinStreamingError? {
        return try {
            val dto = json.decodeFromString<ErrorDataDto>(raw.data)
            ThinStreamingError(
                message = dto.message ?: "Unknown error",
                code = dto.code ?: -1,
                statusCode = dto.statusCode,
                eventTimestamp = raw.timestamp
            )
        } catch (e: SerializationException) {
            Logger.e("Failed to parse ERROR: ${e.message}")
            null
        }
    }
}
