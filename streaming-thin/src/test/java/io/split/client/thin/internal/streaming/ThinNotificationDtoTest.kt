package io.split.client.thin.internal.streaming

import org.junit.Assert.*
import org.junit.Test

class ThinNotificationDtoTest {

    @Test
    fun `RawThinNotificationDto equality and component access`() {
        val dto = RawThinNotificationDto(channel = "ch", data = "d", timestamp = 1L)
        val copy = dto.copy(timestamp = 2L)

        assertEquals("ch", dto.channel)
        assertEquals("d", dto.data)
        assertEquals(1L, dto.timestamp)
        assertEquals(2L, copy.timestamp)
        assertNotEquals(dto, copy)
        assertEquals(dto, dto.copy())
    }

    @Test
    fun `RawThinNotificationDto with null channel`() {
        val dto = RawThinNotificationDto(channel = null, data = "d", timestamp = 0L)
        assertNull(dto.channel)
    }

    @Test
    fun `EvaluationUpdateDataDto equality and component access`() {
        val dto = EvaluationUpdateDataDto(type = "EVALUATION_UPDATE", changeNumber = 42L)
        assertEquals("EVALUATION_UPDATE", dto.type)
        assertEquals(42L, dto.changeNumber)
        assertEquals(dto, dto.copy())
        assertNotEquals(dto, dto.copy(changeNumber = 99L))
    }

    @Test
    fun `ControlDataDto equality and component access`() {
        val dto = ControlDataDto(type = "CONTROL", controlType = "STREAMING_RESUMED")
        assertEquals("CONTROL", dto.type)
        assertEquals("STREAMING_RESUMED", dto.controlType)
        assertEquals(dto, dto.copy())
    }

    @Test
    fun `OccupancyDataDto and OccupancyMetricsDto equality and component access`() {
        val metrics = OccupancyMetricsDto(publishers = 3)
        val dto = OccupancyDataDto(metrics = metrics)

        assertEquals(3, dto.metrics.publishers)
        assertEquals(metrics, dto.metrics.copy())
        assertEquals(dto, dto.copy())
        assertNotEquals(dto, dto.copy(metrics = OccupancyMetricsDto(publishers = 0)))
    }

    @Test
    fun `ErrorDataDto with all optional fields null`() {
        val dto = ErrorDataDto(type = "ERROR")
        assertEquals("ERROR", dto.type)
        assertNull(dto.message)
        assertNull(dto.code)
        assertNull(dto.statusCode)
        assertEquals(dto, dto.copy())
    }

    @Test
    fun `ErrorDataDto with all fields set`() {
        val dto = ErrorDataDto(type = "ERROR", message = "msg", code = 500, statusCode = 503)
        assertEquals("msg", dto.message)
        assertEquals(500, dto.code)
        assertEquals(503, dto.statusCode)
        assertNotEquals(dto, dto.copy(code = 0))
    }
}
