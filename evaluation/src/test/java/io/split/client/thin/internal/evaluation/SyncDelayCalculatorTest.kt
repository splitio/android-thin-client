package io.split.client.thin.internal.evaluation

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class SyncDelayCalculatorTest {

    private val calculator = DefaultSyncDelayCalculator()

    @Test
    fun `returns 500 when hashing algorithm is NONE (0)`() {
        val delay = calculator.calculateDelay("someKey", 60000L, 12345, hashingAlgorithm = 0)
        assertEquals(500L, delay)
    }

    @Test
    fun `returns 500 when hashing algorithm is null`() {
        val delay = calculator.calculateDelay("someKey", 60000L, 12345, hashingAlgorithm = null)
        assertEquals(500L, delay)
    }

    @Test
    fun `delay is not negative with MURMUR3_32`() {
        val delay = calculator.calculateDelay(randomKey(), 600L, 1525, hashingAlgorithm = 1)
        assertTrue(delay >= 0)
    }

    @Test
    fun `delay does not exceed updateIntervalMs with MURMUR3_32`() {
        val delay = calculator.calculateDelay(randomKey(), 600L, 24515, hashingAlgorithm = 1)
        assertTrue(delay <= 600L)
    }

    @Test
    fun `delay defaults to 60s interval when updateIntervalMs is null`() {
        val delay = calculator.calculateDelay(randomKey(), null, 24515, hashingAlgorithm = 1)
        assertTrue(delay >= 0 && delay <= 60000L)
    }

    @Test
    fun `delay defaults to 60s interval when updateIntervalMs is zero`() {
        val delay = calculator.calculateDelay(randomKey(), 0L, 24515, hashingAlgorithm = 1)
        assertTrue(delay >= 0 && delay <= 60000L)
    }

    @Test
    fun `delay defaults to 60s interval when updateIntervalMs is negative`() {
        val delay = calculator.calculateDelay(randomKey(), -1L, 24515, hashingAlgorithm = 1)
        assertTrue(delay >= 0 && delay <= 60000L)
    }

    @Test
    fun `delay is deterministic for same inputs`() {
        val key = "test-key"
        val delay1 = calculator.calculateDelay(key, 60000L, 42, hashingAlgorithm = 1)
        val delay2 = calculator.calculateDelay(key, 60000L, 42, hashingAlgorithm = 1)
        assertEquals(delay1, delay2)
    }

    private fun randomKey() = Random.nextInt().toString()
}
