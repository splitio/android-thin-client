package io.split.client.thin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FallbackTreatmentTest {

    @Test
    fun `constructor with treatment only sets config to null`() {
        val ft = FallbackTreatment("on")

        assertEquals("on", ft.treatment)
        assertNull(ft.config)
    }

    @Test
    fun `constructor with treatment and config stores both`() {
        val ft = FallbackTreatment("on", """{"key":"value"}""")

        assertEquals("on", ft.treatment)
        assertEquals("""{"key":"value"}""", ft.config)
    }


}
