package io.split.client.thin

import io.split.android.client.utils.logger.Logger

/**
 * Configuration for fallback treatments — returned when the Remote Evaluator
 * responds with "control" (e.g., network issues, unknown flags).
 *
 * Use [builder] or the Kotlin DSL via [fallbackTreatmentsConfiguration].
 */
class FallbackTreatmentsConfiguration private constructor(
    val global: FallbackTreatment?,
    val byFlag: Map<String, FallbackTreatment>,
) {
    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FallbackTreatmentsConfiguration) return false
        return global == other.global && byFlag == other.byFlag
    }

    override fun hashCode(): Int {
        var result = global?.hashCode() ?: 0
        result = 31 * result + byFlag.hashCode()
        return result
    }

    override fun toString(): String =
        "FallbackTreatmentsConfiguration(global=$global, byFlag=$byFlag)"

    class Builder {
        private var global: FallbackTreatment? = null
        private val byFlag: MutableMap<String, FallbackTreatment> = mutableMapOf()

        fun global(treatment: FallbackTreatment) = apply {
            if (global != null) {
                Logger.w("Fallback treatments - You had previously set a global fallback. The new value will replace it")
            }
            global = treatment
        }

        fun global(treatment: String) = apply {
            if (global != null) {
                Logger.w("Fallback treatments - You had previously set a global fallback. The new value will replace it")
            }
            global = FallbackTreatment(treatment)
        }

        fun byFlag(flags: Map<String, FallbackTreatment>) = apply {
            for ((key, value) in flags) {
                if (byFlag.containsKey(key)) {
                    Logger.w("Fallback treatments - Duplicate fallback for flag '$key'. Overriding existing value.")
                }
                byFlag[key] = value
            }
        }

        fun byFlagStrings(flags: Map<String, String>) = apply {
            for ((key, value) in flags) {
                if (byFlag.containsKey(key)) {
                    Logger.w("Fallback treatments - Duplicate fallback for flag '$key'. Overriding existing value.")
                }
                byFlag[key] = FallbackTreatment(value)
            }
        }

        fun build(): FallbackTreatmentsConfiguration =
            FallbackTreatmentsConfiguration(global, byFlag.toMap())
    }
}
