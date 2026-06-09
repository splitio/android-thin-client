package io.split.client.thin.internal.streaming

import io.split.android.client.streaming.support.CompressionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinNotificationParserBitmapTest {

    private val parser = ThinNotificationParser()

    @Test
    fun `parse EVALUATIONS_UPDATE with u=0 maps to UNBOUNDED_FETCH_REQUEST`() {
        val raw = RawThinNotification(
            channel = "ch",
            data = """{"type":"EVALUATIONS_UPDATE","changeNumber":1,"u":0}""",
            timestamp = 1000L
        )

        val result = parser.parse(raw) as EvaluationUpdateNotification

        assertEquals(EvaluationUpdateStrategy.UNBOUNDED_FETCH_REQUEST, result.updateStrategy)
    }

    @Test
    fun `parse EVALUATIONS_UPDATE with u=1 maps to BOUNDED_FETCH_REQUEST`() {
        val raw = RawThinNotification(
            channel = "ch",
            data = """{"type":"EVALUATIONS_UPDATE","changeNumber":1,"u":1,"d":"abc","c":1}""",
            timestamp = 1000L
        )

        val result = parser.parse(raw) as EvaluationUpdateNotification

        assertEquals(EvaluationUpdateStrategy.BOUNDED_FETCH_REQUEST, result.updateStrategy)
        assertEquals("abc", result.data)
        assertEquals(CompressionType.GZIP, result.compression)
    }

    @Test
    fun `parse EVALUATIONS_UPDATE with u absent defaults to UNBOUNDED_FETCH_REQUEST`() {
        val raw = RawThinNotification(
            channel = "ch",
            data = """{"type":"EVALUATIONS_UPDATE","changeNumber":5}""",
            timestamp = 1000L
        )

        val result = parser.parse(raw) as EvaluationUpdateNotification

        assertEquals(EvaluationUpdateStrategy.UNBOUNDED_FETCH_REQUEST, result.updateStrategy)
    }

    @Test
    fun `parse EVALUATIONS_UPDATE with u=2 sets updateStrategy to null (unsupported)`() {
        val raw = RawThinNotification(
            channel = "ch",
            data = """{"type":"EVALUATIONS_UPDATE","changeNumber":1,"u":2}""",
            timestamp = 1000L
        )

        val result = parser.parse(raw) as EvaluationUpdateNotification

        assertNull(result.updateStrategy)
    }

    @Test
    fun `parse EVALUATIONS_UPDATE with u=3 sets updateStrategy to null (unsupported)`() {
        val raw = RawThinNotification(
            channel = "ch",
            data = """{"type":"EVALUATIONS_UPDATE","changeNumber":1,"u":3}""",
            timestamp = 1000L
        )

        val result = parser.parse(raw) as EvaluationUpdateNotification

        assertNull(result.updateStrategy)
    }

    @Test
    fun `parse EVALUATIONS_UPDATE with c=0 maps compression to NONE`() {
        val raw = RawThinNotification(
            channel = "ch",
            data = """{"type":"EVALUATIONS_UPDATE","changeNumber":1,"u":1,"d":"payload","c":0}""",
            timestamp = 1000L
        )

        val result = parser.parse(raw) as EvaluationUpdateNotification

        assertEquals(CompressionType.NONE, result.compression)
    }

    @Test
    fun `parse EVALUATIONS_UPDATE with c=2 maps compression to ZLIB`() {
        val raw = RawThinNotification(
            channel = "ch",
            data = """{"type":"EVALUATIONS_UPDATE","changeNumber":1,"u":1,"d":"payload","c":2}""",
            timestamp = 1000L
        )

        val result = parser.parse(raw) as EvaluationUpdateNotification

        assertEquals(CompressionType.ZLIB, result.compression)
    }

    @Test
    fun `parse EVALUATIONS_UPDATE with c absent defaults to NONE`() {
        val raw = RawThinNotification(
            channel = "ch",
            data = """{"type":"EVALUATIONS_UPDATE","changeNumber":1}""",
            timestamp = 1000L
        )

        val result = parser.parse(raw) as EvaluationUpdateNotification

        assertEquals(CompressionType.NONE, result.compression)
    }

    @Test
    fun `parse EVALUATIONS_UPDATE with unknown c value defaults to NONE`() {
        val raw = RawThinNotification(
            channel = "ch",
            data = """{"type":"EVALUATIONS_UPDATE","changeNumber":1,"c":99}""",
            timestamp = 1000L
        )

        val result = parser.parse(raw) as EvaluationUpdateNotification

        assertEquals(CompressionType.NONE, result.compression)
    }

    @Test
    fun `parse EVALUATIONS_UPDATE with d absent has null data`() {
        val raw = RawThinNotification(
            channel = "ch",
            data = """{"type":"EVALUATIONS_UPDATE","changeNumber":1}""",
            timestamp = 1000L
        )

        val result = parser.parse(raw) as EvaluationUpdateNotification

        assertNull(result.data)
    }
}
