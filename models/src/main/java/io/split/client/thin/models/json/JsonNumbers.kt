package io.split.client.thin.models.json

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import java.math.BigDecimal

/**
 * Converts a Number to a JsonElement using plain decimal notation.
 *
 * This function addresses a critical issue with Double.toString() which emits scientific notation
 * for small magnitudes (e.g., 0.0003 → "3.0E-4"), which is incorrect for JSON wire format.
 *
 * Behavior:
 * - For Double and Float: converts to plain decimal string without scientific notation.
 *   Non-finite values (NaN, +Infinity, -Infinity) are converted to JsonNull since JSON
 *   has no standard representation for these values.
 * - For integral types (Int, Long, Short, Byte): uses standard JsonPrimitive conversion.
 * - For other Number types: converts to plain decimal string representation.
 *
 * @return JsonElement representation using plain decimal notation, never scientific notation.
 */
@OptIn(ExperimentalSerializationApi::class)
fun Number.toPlainJsonElement(): JsonElement {
    return when (this) {
        is Double -> {
            if (isFinite()) {
                JsonUnquotedLiteral(BigDecimal.valueOf(this).stripTrailingZeros().toPlainString())
            } else {
                JsonNull
            }
        }
        is Float -> {
            if (isFinite()) {
                JsonUnquotedLiteral(BigDecimal.valueOf(this.toDouble()).stripTrailingZeros().toPlainString())
            } else {
                JsonNull
            }
        }
        is Int -> JsonPrimitive(this)
        is Long -> JsonPrimitive(this)
        is Short -> JsonPrimitive(this)
        is Byte -> JsonPrimitive(this)
        is java.math.BigInteger -> JsonUnquotedLiteral(this.toString())
        is java.math.BigDecimal -> JsonUnquotedLiteral(this.stripTrailingZeros().toPlainString())
        else -> {
            val d = this.toDouble()
            if (d.isFinite()) {
                JsonUnquotedLiteral(BigDecimal.valueOf(d).stripTrailingZeros().toPlainString())
            } else {
                JsonNull
            }
        }
    }
}
