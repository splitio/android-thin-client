package io.split.client.thin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SdkReadyMetadataTest {

    @Test
    fun `ready metadata defaults are null`() {
        val metadata = SdkReadyMetadata()
        assertNull(metadata.isInitialCacheLoad)
        assertNull(metadata.lastUpdateTimestamp)
    }

    @Test
    fun `ready metadata stores provided values`() {
        val metadata = SdkReadyMetadata(
            isInitialCacheLoad = true,
            lastUpdateTimestamp = 1234L,
        )
        assertEquals(true, metadata.isInitialCacheLoad)
        assertEquals(1234L, metadata.lastUpdateTimestamp)
    }

    @Test
    fun `ready metadata equality follows data class contract`() {
        val a = SdkReadyMetadata(isInitialCacheLoad = false, lastUpdateTimestamp = 99L)
        val b = SdkReadyMetadata(isInitialCacheLoad = false, lastUpdateTimestamp = 99L)
        val c = SdkReadyMetadata(isInitialCacheLoad = true, lastUpdateTimestamp = 99L)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }
}
