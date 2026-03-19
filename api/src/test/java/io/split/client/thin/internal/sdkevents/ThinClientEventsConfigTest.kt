package io.split.client.thin.internal.sdkevents

import io.split.client.thin.SplitEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinClientEventsConfigTest {

    private val config = ThinClientEventsConfig.create()

    @Test
    fun `SDK_READY requires all EVALUATIONS_SYNC_COMPLETE`() {
        val requireAll = config.requireAll
        assertTrue(requireAll.containsKey(SplitEvent.SDK_READY))
        assertEquals(
            setOf(SdkInternalEvent.EVALUATIONS_SYNC_COMPLETE),
            requireAll[SplitEvent.SDK_READY]
        )
    }

    @Test
    fun `SDK_READY_FROM_CACHE requires any of EVALUATIONS_LOADED_FROM_STORAGE or EVALUATIONS_SYNC_COMPLETE`() {
        val requireAny = config.requireAny
        assertTrue(requireAny.containsKey(SplitEvent.SDK_READY_FROM_CACHE))
        val groups = requireAny[SplitEvent.SDK_READY_FROM_CACHE]!!
        val flatInternals = groups.flatten().toSet()
        assertTrue(flatInternals.contains(SdkInternalEvent.EVALUATIONS_LOADED_FROM_STORAGE))
        assertTrue(flatInternals.contains(SdkInternalEvent.EVALUATIONS_SYNC_COMPLETE))
    }

    @Test
    fun `SDK_READY_TIMEOUT requires any of SDK_READY_TIMEOUT_REACHED`() {
        val requireAny = config.requireAny
        assertTrue(requireAny.containsKey(SplitEvent.SDK_READY_TIMEOUT))
        val groups = requireAny[SplitEvent.SDK_READY_TIMEOUT]!!
        val flatInternals = groups.flatten().toSet()
        assertTrue(flatInternals.contains(SdkInternalEvent.SDK_READY_TIMEOUT_REACHED))
    }

    @Test
    fun `SDK_UPDATE requires any of EVALUATIONS_UPDATED`() {
        val requireAny = config.requireAny
        assertTrue(requireAny.containsKey(SplitEvent.SDK_UPDATE))
        val groups = requireAny[SplitEvent.SDK_UPDATE]!!
        val flatInternals = groups.flatten().toSet()
        assertTrue(flatInternals.contains(SdkInternalEvent.EVALUATIONS_UPDATED))
    }

    @Test
    fun `SDK_READY has SDK_READY_FROM_CACHE as prerequisite`() {
        val prerequisites = config.prerequisites
        assertTrue(prerequisites.containsKey(SplitEvent.SDK_READY))
        assertTrue(prerequisites[SplitEvent.SDK_READY]!!.contains(SplitEvent.SDK_READY_FROM_CACHE))
    }

    @Test
    fun `SDK_UPDATE has SDK_READY as prerequisite`() {
        val prerequisites = config.prerequisites
        assertTrue(prerequisites.containsKey(SplitEvent.SDK_UPDATE))
        assertTrue(prerequisites[SplitEvent.SDK_UPDATE]!!.contains(SplitEvent.SDK_READY))
    }

    @Test
    fun `SDK_READY_TIMEOUT is suppressed by SDK_READY`() {
        val suppressedBy = config.suppressedBy
        assertTrue(suppressedBy.containsKey(SplitEvent.SDK_READY_TIMEOUT))
        assertTrue(suppressedBy[SplitEvent.SDK_READY_TIMEOUT]!!.contains(SplitEvent.SDK_READY))
    }

    @Test
    fun `SDK_READY executes at most once`() {
        val limits = config.executionLimits
        assertEquals(1, limits[SplitEvent.SDK_READY])
    }

    @Test
    fun `SDK_READY_FROM_CACHE executes at most once`() {
        val limits = config.executionLimits
        assertEquals(1, limits[SplitEvent.SDK_READY_FROM_CACHE])
    }

    @Test
    fun `SDK_READY_TIMEOUT executes at most once`() {
        val limits = config.executionLimits
        assertEquals(1, limits[SplitEvent.SDK_READY_TIMEOUT])
    }

    @Test
    fun `SDK_UPDATE has unlimited executions`() {
        val limits = config.executionLimits
        assertEquals(-1, limits[SplitEvent.SDK_UPDATE])
    }

    @Test
    fun `evaluation order does not contain SDK_READY before SDK_READY_FROM_CACHE`() {
        val order = config.evaluationOrder
        val readyFromCacheIndex = order.indexOf(SplitEvent.SDK_READY_FROM_CACHE)
        val readyIndex = order.indexOf(SplitEvent.SDK_READY)
        // SDK_READY_FROM_CACHE is a prerequisite of SDK_READY, so it must come first
        assertFalse(readyIndex != -1 && readyFromCacheIndex != -1 && readyIndex < readyFromCacheIndex)
    }
}
