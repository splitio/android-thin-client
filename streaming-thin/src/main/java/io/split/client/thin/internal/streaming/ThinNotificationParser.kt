package io.split.client.thin.internal.streaming

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import io.split.android.client.utils.logger.Logger

internal class ThinNotificationParser(
    private val gson: Gson = Gson()
) {

    fun parseRaw(jsonData: String): RawThinNotification? {
        return try {
            gson.fromJson(jsonData, RawThinNotification::class.java)
        } catch (e: JsonSyntaxException) {
            Logger.e("Failed to parse raw notification: ${e.message}")
            null
        }
    }

    fun parse(raw: RawThinNotification): ThinNotification? {
        return try {
            when {
                raw.data.contains("\"type\":\"EVALUATION_UPDATE\"") -> parseEvaluationUpdate(raw)
                raw.data.contains("\"type\":\"CONTROL\"") -> parseControl(raw)
                raw.data.contains("\"type\":\"OCCUPANCY\"") -> parseOccupancy(raw)
                raw.data.contains("\"type\":\"ERROR\"") -> parseError(raw)
                else -> {
                    Logger.w("Unknown notification type: ${raw.data}")
                    null
                }
            }
        } catch (e: Exception) {
            Logger.e("Failed to parse notification: ${e.message}")
            null
        }
    }

    private fun parseEvaluationUpdate(raw: RawThinNotification): EvaluationUpdateNotification? {
        val dataMap = gson.fromJson(raw.data, Map::class.java)
        val changeNumber = (dataMap["changeNumber"] as? Number)?.toLong() ?: return null
        return EvaluationUpdateNotification(changeNumber, raw.channel, raw.timestamp)
    }

    private fun parseControl(raw: RawThinNotification): ThinControlNotification? {
        val dataMap = gson.fromJson(raw.data, Map::class.java)
        val controlTypeStr = dataMap["controlType"] as? String ?: return null
        val controlType = when (controlTypeStr) {
            "STREAMING_RESUMED" -> ThinControlNotification.ControlType.STREAMING_RESUMED
            "STREAMING_DISABLED" -> ThinControlNotification.ControlType.STREAMING_DISABLED
            "STREAMING_PAUSED" -> ThinControlNotification.ControlType.STREAMING_PAUSED
            "STREAMING_RESET" -> ThinControlNotification.ControlType.STREAMING_RESET
            else -> return null
        }
        return ThinControlNotification(controlType, raw.channel, raw.timestamp)
    }

    private fun parseOccupancy(raw: RawThinNotification): ThinOccupancyNotification? {
        val dataMap = gson.fromJson(raw.data, Map::class.java)
        val publishers = (dataMap["publishers"] as? Number)?.toInt() ?: return null
        return ThinOccupancyNotification(publishers, raw.channel, raw.timestamp)
    }

    private fun parseError(raw: RawThinNotification): ThinStreamingError? {
        val dataMap = gson.fromJson(raw.data, Map::class.java)
        val message = dataMap["message"] as? String ?: "Unknown error"
        val code = (dataMap["code"] as? Number)?.toInt() ?: -1
        val statusCode = (dataMap["statusCode"] as? Number)?.toInt()
        return ThinStreamingError(message, code, statusCode, raw.timestamp)
    }
}
