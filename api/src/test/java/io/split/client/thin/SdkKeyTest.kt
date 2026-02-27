package io.split.client.thin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SdkKeyTest {

    @Test
    fun `sdk key constructor`() {
        val sdkKey = SdkKey("test-sdk-key")
        assertEquals("test-sdk-key", sdkKey.sdkKey)
    }

    @Test
    fun `sdk key equality`() {
        val sdkKey1 = SdkKey("test-sdk-key")
        val sdkKey2 = SdkKey("test-sdk-key")
        assertEquals(sdkKey1, sdkKey2)
    }

    @Test
    fun `sdk key inequality`() {
        val sdkKey1 = SdkKey("test-sdk-key")
        val sdkKey2 = SdkKey("test-sdk-key-2")
        assertNotEquals(sdkKey1, sdkKey2)
    }

    @Test
    fun `sdk key hashCode`() {
        val sdkKey = SdkKey("test-sdk-key")
        assertEquals(sdkKey.hashCode(), "test-sdk-key".hashCode())
    }

    @Test
    fun `sdk key toString`() {
        val sdkKey = SdkKey("test-sdk-key")
        assertEquals("SdkKey(sdkKey=test-sdk-key)", sdkKey.toString())
    }
}
