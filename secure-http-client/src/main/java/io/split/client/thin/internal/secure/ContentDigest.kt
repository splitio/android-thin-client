package io.split.client.thin.internal.secure

import com.goncalossilva.murmurhash.MurmurHash3
import java.nio.ByteBuffer
import java.util.Base64

internal object ContentDigest {

    fun compute(target: EvaluationTarget): String {
        val attrsJson = serializeAttributes(target.attributes)
        val input = "${target.matchingKey}:$attrsJson"
        val inputBytes = input.toByteArray(Charsets.UTF_8)
        val hash = MurmurHash3().hash128x64(inputBytes)
        val first8Bytes = ByteBuffer.allocate(8).putLong(hash[0].toLong()).array()
        return Base64.getEncoder().withoutPadding().encodeToString(first8Bytes)
    }

    private fun serializeAttributes(attributes: Map<String, Any?>?): String {
        if (attributes.isNullOrEmpty()) return "{}"
        val sb = StringBuilder("{")
        attributes.keys.sorted().forEachIndexed { i, key ->
            if (i > 0) sb.append(",")
            sb.append("\"$key\":")
            sb.append(serializeValue(attributes[key]))
        }
        sb.append("}")
        return sb.toString()
    }

    private fun serializeValue(value: Any?): String = when (value) {
        null -> "null"
        is Boolean -> value.toString()
        is Number -> value.toString()
        else -> "\"$value\""
    }
}
