package io.split.client.thin.internal.streaming

import android.util.Base64
import io.split.android.client.streaming.support.CompressionType
import io.split.android.client.streaming.support.CompressionUtilProvider
import com.goncalossilva.murmurhash.MurmurHash3
import java.math.BigInteger

open class EvaluationPayloadDecoder(
    private val compressionProvider: CompressionUtilProvider,
    private val base64Decode: (String) -> ByteArray? = { encoded ->
        try { Base64.decode(encoded, Base64.DEFAULT) } catch (e: Exception) { null }
    },
) {

    open fun decodeAsBytes(data: String, compression: CompressionType): ByteArray {
        val decoded = base64Decode(data)
            ?: throw IllegalArgumentException("Failed to base64-decode payload")
        val util = compressionProvider.get(compression)
            ?: throw IllegalArgumentException("No compression util for $compression")
        return util.decompress(decoded)
            ?: throw IllegalArgumentException("Failed to decompress payload with $compression")
    }

    fun hashKey(matchingKey: String): BigInteger {
        val lane0 = MurmurHash3(0u).hash128x64(matchingKey.toByteArray(Charsets.UTF_8))[0]
        return BigInteger(java.lang.Long.toBinaryString(lane0.toLong()), 2)
    }

    fun computeKeyIndex(hashed: BigInteger, bitmapLen: Int): Int =
        hashed.remainder(BigInteger.valueOf(bitmapLen * 8L)).toInt()

    fun isKeyInBitmap(bitmap: ByteArray, index: Int): Boolean {
        val byteIdx = index / 8
        if (byteIdx > bitmap.size - 1) return false
        val bit = 1 shl (index % 8)
        return (bitmap[byteIdx].toInt() and bit) != 0
    }
}
