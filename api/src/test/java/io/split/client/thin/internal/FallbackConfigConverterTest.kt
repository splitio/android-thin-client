package io.split.client.thin.internal

import io.split.client.thin.FallbackTreatment
import io.split.client.thin.FallbackTreatmentsConfiguration
import io.split.client.thin.SplitClientConfig
import io.split.android.client.fallback.FallbackTreatmentsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FallbackConfigConverterTest {

    @Test
    fun `toInternal converts global treatment`() {
        val wrapper = FallbackTreatmentsConfiguration.builder()
            .global("off")
            .build()

        val internal = wrapper.toInternal()

        assertEquals("off", internal.global?.treatment)
        assertNull(internal.global?.config)
    }

    @Test
    fun `toInternal converts global treatment with config`() {
        val wrapper = FallbackTreatmentsConfiguration.builder()
            .global(FallbackTreatment("off", """{"key":"val"}"""))
            .build()

        val internal = wrapper.toInternal()

        assertEquals("off", internal.global?.treatment)
        assertEquals("""{"key":"val"}""", internal.global?.config)
    }

    @Test
    fun `toInternal converts byFlag entries`() {
        val wrapper = FallbackTreatmentsConfiguration.builder()
            .byFlagStrings(mapOf("flag_a" to "on", "flag_b" to "off"))
            .build()

        val internal = wrapper.toInternal()

        assertEquals("on", internal.byFlag["flag_a"]?.treatment)
        assertEquals("off", internal.byFlag["flag_b"]?.treatment)
    }

    @Test
    fun `toInternal converts byFlag entry with config`() {
        val wrapper = FallbackTreatmentsConfiguration.builder()
            .byFlag(mapOf("flag_a" to FallbackTreatment("on", "cfg")))
            .build()

        val internal = wrapper.toInternal()

        assertEquals("on", internal.byFlag["flag_a"]?.treatment)
        assertEquals("cfg", internal.byFlag["flag_a"]?.config)
    }

    @Test
    fun `toInternal with no global produces null global`() {
        val wrapper = FallbackTreatmentsConfiguration.builder()
            .byFlagStrings(mapOf("flag_a" to "on"))
            .build()

        val internal = wrapper.toInternal()

        assertNull(internal.global)
    }

    @Test
    fun `toInternal with empty byFlag produces empty map`() {
        val wrapper = FallbackTreatmentsConfiguration.builder()
            .global("off")
            .build()

        val internal = wrapper.toInternal()

        assertEquals(emptyMap<String, Any>(), internal.byFlag)
    }

    @Test
    fun `toInternal with both global and byFlag converts all fields`() {
        val wrapper = FallbackTreatmentsConfiguration.builder()
            .global("off")
            .byFlagStrings(mapOf("flag_a" to "on"))
            .build()

        val internal = wrapper.toInternal()

        assertEquals("off", internal.global?.treatment)
        assertEquals("on", internal.byFlag["flag_a"]?.treatment)
    }
}

class BuildFallbackCalculatorTest {

    @Test
    fun `buildFallbackCalculator returns null when config is null`() {
        val result = DefaultSplitFactory.buildFallbackCalculator(null)

        assertNull(result)
    }

    @Test
    fun `buildFallbackCalculator returns null when fallbackTreatments is null`() {
        val config = SplitClientConfig.Builder().build()

        val result = DefaultSplitFactory.buildFallbackCalculator(config)

        assertNull(result)
    }

    @Test
    fun `buildFallbackCalculator returns calculator when fallbackTreatments is set`() {
        val config = SplitClientConfig.Builder()
            .fallbackTreatments(
                FallbackTreatmentsConfiguration.builder()
                    .global("off")
                    .build()
            )
            .build()

        val result: FallbackTreatmentsCalculator? = DefaultSplitFactory.buildFallbackCalculator(config)

        assertNotNull(result)
    }
}
