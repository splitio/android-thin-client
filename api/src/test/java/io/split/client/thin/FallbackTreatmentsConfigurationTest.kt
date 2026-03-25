package io.split.client.thin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FallbackTreatmentsConfigurationTest {

    // ── Builder: global ──────────────────────────────────────────────────────

    @Test
    fun `global(String) sets global treatment with null config`() {
        val config = FallbackTreatmentsConfiguration.builder()
            .global("off")
            .build()

        assertEquals("off", config.global?.treatment)
        assertNull(config.global?.config)
    }

    @Test
    fun `global(FallbackTreatment) sets global treatment and config`() {
        val config = FallbackTreatmentsConfiguration.builder()
            .global(FallbackTreatment("off", """{"k":"v"}"""))
            .build()

        assertEquals("off", config.global?.treatment)
        assertEquals("""{"k":"v"}""", config.global?.config)
    }

    @Test
    fun `global defaults to null when not set`() {
        val config = FallbackTreatmentsConfiguration.builder().build()

        assertNull(config.global)
    }

    // ── Builder: byFlag ───────────────────────────────────────────────────────

    @Test
    fun `byFlagStrings stores treatments by flag name`() {
        val config = FallbackTreatmentsConfiguration.builder()
            .byFlagStrings(mapOf("flag_a" to "on", "flag_b" to "off"))
            .build()

        assertEquals("on", config.byFlag["flag_a"]?.treatment)
        assertEquals("off", config.byFlag["flag_b"]?.treatment)
    }

    @Test
    fun `byFlag(Map) stores FallbackTreatment objects`() {
        val config = FallbackTreatmentsConfiguration.builder()
            .byFlag(mapOf("flag_a" to FallbackTreatment("on", "cfg")))
            .build()

        assertEquals("on", config.byFlag["flag_a"]?.treatment)
        assertEquals("cfg", config.byFlag["flag_a"]?.config)
    }

    @Test
    fun `byFlagStrings accumulates across multiple calls`() {
        val config = FallbackTreatmentsConfiguration.builder()
            .byFlagStrings(mapOf("flag_a" to "on"))
            .byFlagStrings(mapOf("flag_b" to "off"))
            .build()

        assertEquals(2, config.byFlag.size)
    }

    @Test
    fun `byFlag defaults to empty map when not set`() {
        val config = FallbackTreatmentsConfiguration.builder().build()

        assertEquals(emptyMap<String, FallbackTreatment>(), config.byFlag)
    }

    // ── equals / hashCode ─────────────────────────────────────────────────────

    @Test
    fun `equal configurations are equal`() {
        val a = FallbackTreatmentsConfiguration.builder()
            .global("off")
            .byFlagStrings(mapOf("flag_a" to "on"))
            .build()
        val b = FallbackTreatmentsConfiguration.builder()
            .global("off")
            .byFlagStrings(mapOf("flag_a" to "on"))
            .build()

        assertEquals(a, b)
    }

    @Test
    fun `configurations with different global are not equal`() {
        val a = FallbackTreatmentsConfiguration.builder().global("off").build()
        val b = FallbackTreatmentsConfiguration.builder().global("on").build()

        assertNotEquals(a, b)
    }

    @Test
    fun `configurations with different byFlag are not equal`() {
        val a = FallbackTreatmentsConfiguration.builder()
            .byFlagStrings(mapOf("flag_a" to "on")).build()
        val b = FallbackTreatmentsConfiguration.builder()
            .byFlagStrings(mapOf("flag_a" to "off")).build()

        assertNotEquals(a, b)
    }

    @Test
    fun `equal configurations have equal hashCodes`() {
        val a = FallbackTreatmentsConfiguration.builder().global("off").build()
        val b = FallbackTreatmentsConfiguration.builder().global("off").build()

        assertEquals(a.hashCode(), b.hashCode())
    }
}
