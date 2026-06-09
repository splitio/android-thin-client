package io.split.client.thin.internal.streaming

import io.split.android.client.streaming.support.CompressionType
import io.split.android.client.streaming.support.CompressionUtilProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.util.Base64

class EvaluationPayloadDecoderTest {

    // Inject JVM Base64 so tests don't need Robolectric (android.util.Base64 only runs on device)
    private val jvmBase64Decode: (String) -> ByteArray? = { encoded ->
        try { Base64.getDecoder().decode(encoded) } catch (e: Exception) { null }
    }

    private lateinit var decoder: EvaluationPayloadDecoder

    // Known gzip/zlib test payloads from android-client TestingData.
    // Both bitmaps contain exactly these 10 keys:
    private val gzipBitmapBase64 =
        "H4sIAAAAAAAA/2IYBfgAx0A7YBTgB4wD7YABAAID7QC6g5EYy8MEMA20A+gMFAbaAYMZDPXqlGWgHTAKRsEoGAWjgCzQQFjJkKqiiPAPAQAIAAD//5L7VQwAEAAA"

    private val zlibBitmapBase64 =
        "eJxiGAX4AMdAO2AU4AeMA+2AAQACA+0AuoORGMvDBDANtAPoDBQG2gGDGQz16pRloB0wCkbBKBgFo4As0EBYyZCqoojwDwEACAAA//+W/QFR"

    private val knownInBitmapKeys = listOf(
        "603516ce-1243-400b-b919-0dce5d8aecfd",
        "88f8b33b-f858-4aea-bea2-a5f066bab3ce",
        "375903c8-6f62-4272-88f1-f8bcd304c7ae",
        "18c936ad-0cd2-490d-8663-03eaa23a5ef1",
        "bfd4a824-0cde-4f11-9700-2b4c5ad6f719",
        "4588c4f6-3d18-452a-bc4a-47d7abfd23df",
        "42bcfe02-d268-472f-8ed5-e6341c33b4f7",
        "2a7cae0e-85a2-443e-9d7c-7157b7c5960a",
        "4b0b0467-3fe1-43d1-a3d5-937c0a5473b1",
        "09025e90-d396-433a-9292-acef23cf0ad1",
    )

    @Before
    fun setUp() {
        decoder = EvaluationPayloadDecoder(CompressionUtilProvider(), jvmBase64Decode)
    }

    // ── decodeAsBytes ──────────────────────────────────────────────────────────

    @Test
    fun `decodeAsBytes throws when base64 decode returns null`() {
        val decoderWithNullBase64 = EvaluationPayloadDecoder(CompressionUtilProvider()) { null }
        try {
            decoderWithNullBase64.decodeAsBytes("anyPayload", CompressionType.NONE)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("base64"))
        }
    }

    @Test
    fun `decodeAsBytes throws when decompression returns null`() {
        val nullDecompressProvider = object : io.split.android.client.streaming.support.CompressionUtilProvider() {
            override fun get(type: CompressionType): io.split.android.client.streaming.support.CompressionUtil? = null
        }
        val d = EvaluationPayloadDecoder(nullDecompressProvider, jvmBase64Decode)
        try {
            d.decodeAsBytes(Base64.getEncoder().encodeToString(byteArrayOf(1, 2)), CompressionType.GZIP)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("compression util"))
        }
    }

    @Test
    fun `decodeAsBytes NONE returns raw bytes`() {
        val raw = ByteArray(4) { it.toByte() }
        val encoded = Base64.getEncoder().encodeToString(raw)

        val result = decoder.decodeAsBytes(encoded, CompressionType.NONE)

        assertEquals(raw.toList(), result.toList())
    }

    @Test
    fun `decodeAsBytes GZIP returns non-empty result`() {
        val result = decoder.decodeAsBytes(gzipBitmapBase64, CompressionType.GZIP)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `decodeAsBytes ZLIB returns non-empty result`() {
        val result = decoder.decodeAsBytes(zlibBitmapBase64, CompressionType.ZLIB)
        assertTrue(result.isNotEmpty())
    }

    // ── hashKey ────────────────────────────────────────────────────────────────

    @Test
    fun `hashKey returns non-negative BigInteger`() {
        assertTrue(decoder.hashKey("someKey") >= BigInteger.ZERO)
    }

    @Test
    fun `hashKey is deterministic`() {
        assertEquals(decoder.hashKey("myKey"), decoder.hashKey("myKey"))
    }

    // ── computeKeyIndex ───────────────────────────────────────────────────────

    @Test
    fun `computeKeyIndex returns value in range 0 until bitmapLen times 8`() {
        val bitmapLen = 10
        val index = decoder.computeKeyIndex(decoder.hashKey("testUser"), bitmapLen)
        assertTrue(index >= 0)
        assertTrue(index < bitmapLen * 8)
    }

    // ── isKeyInBitmap ─────────────────────────────────────────────────────────

    @Test
    fun `isKeyInBitmap returns true when bit is set`() {
        val bitmap = byteArrayOf(0x01) // bit 0 set
        assertTrue(decoder.isKeyInBitmap(bitmap, 0))
    }

    @Test
    fun `isKeyInBitmap returns false when bit is not set`() {
        val bitmap = byteArrayOf(0x01) // only bit 0 set
        assertFalse(decoder.isKeyInBitmap(bitmap, 1))
    }

    @Test
    fun `isKeyInBitmap returns false for out-of-range index`() {
        val bitmap = byteArrayOf(0xFF.toByte()) // 1 byte covering bits 0-7
        assertFalse(decoder.isKeyInBitmap(bitmap, 8))
    }

    @Test
    fun `isKeyInBitmap respects individual bit positions within a byte`() {
        // 0xAA = 0b10101010 → bits 1,3,5,7 set; bits 0,2,4,6 clear
        val bitmap = byteArrayOf(0xAA.toByte())
        assertFalse(decoder.isKeyInBitmap(bitmap, 0))
        assertTrue(decoder.isKeyInBitmap(bitmap, 1))
        assertFalse(decoder.isKeyInBitmap(bitmap, 2))
        assertTrue(decoder.isKeyInBitmap(bitmap, 3))
        assertFalse(decoder.isKeyInBitmap(bitmap, 4))
        assertTrue(decoder.isKeyInBitmap(bitmap, 5))
        assertFalse(decoder.isKeyInBitmap(bitmap, 6))
        assertTrue(decoder.isKeyInBitmap(bitmap, 7))
    }

    // ── end-to-end bitmap checks (mirror android-client integration vectors) ──

    @Test
    fun `gzip bitmap contains all 10 known keys`() {
        val bitmap = decoder.decodeAsBytes(gzipBitmapBase64, CompressionType.GZIP)
        for (key in knownInBitmapKeys) {
            val idx = decoder.computeKeyIndex(decoder.hashKey(key), bitmap.size)
            assertTrue("key $key must be in gzip bitmap", decoder.isKeyInBitmap(bitmap, idx))
        }
    }

    @Test
    fun `zlib bitmap contains all 10 known keys`() {
        val bitmap = decoder.decodeAsBytes(zlibBitmapBase64, CompressionType.ZLIB)
        for (key in knownInBitmapKeys) {
            val idx = decoder.computeKeyIndex(decoder.hashKey(key), bitmap.size)
            assertTrue("key $key must be in zlib bitmap", decoder.isKeyInBitmap(bitmap, idx))
        }
    }
}
