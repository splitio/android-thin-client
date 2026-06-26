package io.split.client.thin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SdkUpdateMetadataTest {

    @Test
    fun `update metadata defaults to null type and empty names`() {
        val metadata = SdkUpdateMetadata()
        assertNull(metadata.type)
        assertEquals(emptyList<String>(), metadata.names)
    }

    @Test
    fun `update metadata stores provided values`() {
        val names = listOf("flag_a", "flag_b")
        val metadata = SdkUpdateMetadata(
            type = SdkUpdateMetadata.Type.FLAGS_UPDATE,
            names = names,
        )

        assertEquals(SdkUpdateMetadata.Type.FLAGS_UPDATE, metadata.type)
        assertEquals(names, metadata.names)
    }

    @Test
    fun `update metadata equality follows data class contract`() {
        val a = SdkUpdateMetadata(
            type = SdkUpdateMetadata.Type.SEGMENTS_UPDATE,
            names = emptyList(),
        )
        val b = SdkUpdateMetadata(
            type = SdkUpdateMetadata.Type.SEGMENTS_UPDATE,
            names = emptyList(),
        )
        val c = SdkUpdateMetadata(
            type = SdkUpdateMetadata.Type.FLAGS_UPDATE,
            names = listOf("flag_a"),
        )

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }
}
