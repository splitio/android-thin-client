package io.split.client.thin.internal

import io.split.android.client.utils.logger.Logger
import io.split.client.thin.Key
import io.split.client.thin.SdkKey

internal interface InputValidator {
    fun validateSdkKey(sdkKey: SdkKey): Boolean
    fun validateKey(key: Key): Boolean
    fun validateFlagName(flagName: String): Boolean
    fun validateEventValue(value: Double?, properties: Map<String, Any?>?): Boolean
}

internal class DefaultInputValidator : InputValidator {

    private companion object {
        const val MAX_KEY_LENGTH = 250
    }

    override fun validateSdkKey(sdkKey: SdkKey): Boolean {
        if (sdkKey.sdkKey.isBlank()) {
            Logger.w("SDK key cannot be empty")
            return false
        }
        return true
    }

    override fun validateFlagName(flagName: String): Boolean {
        if (flagName.isBlank()) {
            Logger.w("Flag name cannot be empty")
            return false
        }
        val trimmed = flagName.trim()
        if (trimmed != flagName) {
            Logger.w("Flag name '$flagName' has extra whitespace, trimming")
        }
        return true
    }

    override fun validateEventValue(value: Double?, properties: Map<String, Any?>?): Boolean {
        if (value != null && !value.isFinite()) {
            Logger.w("Event value must be a finite number, got: $value")
            return false
        }
        properties?.forEach { (key, v) ->
            if (v is Number && !v.toDouble().isFinite()) {
                Logger.w("Event property '$key' must be a finite number, got: $v")
                return false
            }
        }
        return true
    }

    override fun validateKey(key: Key): Boolean {
        if (key.matchingKey.isBlank()) {
            Logger.w("Matching key cannot be empty")
            return false
        }
        if (key.matchingKey.length > MAX_KEY_LENGTH) {
            Logger.w("Matching key is too long: ${key.matchingKey.length} characters (max $MAX_KEY_LENGTH)")
            return false
        }
        val bk = key.bucketingKey
        if (bk != null) {
            if (bk.isBlank()) {
                Logger.w("Bucketing key cannot be empty")
                return false
            }
            if (bk.length > MAX_KEY_LENGTH) {
                Logger.w("Bucketing key is too long: ${bk.length} characters (max $MAX_KEY_LENGTH)")
                return false
            }
        }
        return true
    }
}
