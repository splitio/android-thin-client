package io.split.client.thin.internal

import io.split.client.thin.EvaluationOptions
import io.split.client.thin.EvaluationResult
import io.split.client.thin.Key
import io.split.client.thin.SplitClient
import io.split.client.thin.SplitEventListener
import io.split.client.thin.SplitVoidCallback
import io.split.client.thin.Target
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class DefaultClientManagerTest {

    private val testScope = TestScope()
    private val key1 = Key("user-1")
    private val key2 = Key("user-2")
    private val target1 = Target(key1, trafficType = "user")
    private val target1v2 = Target(key1, trafficType = "other")
    private val target2 = Target(key2, trafficType = "user")

    private val createdClients = mutableListOf<FakeSplitClient>()

    private lateinit var manager: DefaultClientManager

    @Before
    fun setUp() {
        createdClients.clear()
        manager = DefaultClientManager(
            clientFactory = { _ ->
                FakeSplitClient().also { createdClients.add(it) }
            },
            scope = testScope,
        )
    }

    @Test
    fun `getOrCreate with new key creates one client via factory`() {
        val client = manager.getOrCreate(target1)

        assertEquals(1, createdClients.size)
        assertSame(createdClients[0], client)
        assertEquals(0, createdClients[0].setTargetCallCount)
    }

    @Test
    fun `getOrCreate with same key returns same instance without creating a new one`() {
        val first = manager.getOrCreate(target1)
        val second = manager.getOrCreate(target1)

        assertSame(first, second)
        assertEquals(1, createdClients.size)
    }

    @Test
    fun `getOrCreate with same key and same target does not fire setTarget`() =
        testScope.runTest {
            manager.getOrCreate(target1)
            manager.getOrCreate(target1)

            testScheduler.advanceUntilIdle()

            assertEquals(0, createdClients[0].setTargetCallCount)
        }

    @Test
    fun `getOrCreate with same key but different target returns same instance and fires setTarget`() =
        testScope.runTest {
            val first = manager.getOrCreate(target1)
            val second = manager.getOrCreate(target1v2)

            assertSame(first, second)
            assertEquals(1, createdClients.size)

            // The fire-and-forget launch is only scheduled at this point; advance the
            // TestCoroutineScheduler so it actually executes before we assert.
            testScheduler.advanceUntilIdle()

            assertEquals(1, createdClients[0].setTargetCallCount)
            assertEquals(target1v2, createdClients[0].lastSetTarget)
        }

    @Test
    fun `destroy with known key removes client and calls destroy on it`() = testScope.runTest {
        manager.getOrCreate(target1)
        manager.destroy(key1)

        assertEquals(1, createdClients[0].destroyCallCount)
        // Getting the same key again should create a fresh client
        val fresh = manager.getOrCreate(target1)
        assertEquals(2, createdClients.size)
        assertSame(createdClients[1], fresh)
    }

    @Test
    fun `destroy with unknown key is a no-op`() = testScope.runTest {
        manager.destroy(Key("ghost"))
        // No exception thrown; nothing to assert beyond reaching here
    }

    @Test
    fun `destroyAll destroys every registered client and clears the registry`() =
        testScope.runTest {
            manager.getOrCreate(target1)
            manager.getOrCreate(target2)
            manager.destroyAll()

            assertEquals(2, createdClients.size)
            createdClients.forEach { assertEquals(1, it.destroyCallCount) }

            // Registry should be empty: next getOrCreate creates a new client
            manager.getOrCreate(target1)
            assertEquals(3, createdClients.size)
        }

    @Test
    fun `destroyAll continues destroying remaining clients if one throws`() =
        testScope.runTest {
            val throwingFactory = { _: Target ->
                FakeSplitClient(destroyShouldThrow = true).also { createdClients.add(it) }
            }
            val normalFactory = { _: Target ->
                FakeSplitClient().also { createdClients.add(it) }
            }
            var factoryCallCount = 0
            val mixedManager = DefaultClientManager(
                clientFactory = { target ->
                    if (factoryCallCount++ == 0) throwingFactory(target) else normalFactory(target)
                },
                scope = testScope,
            )
            mixedManager.getOrCreate(target1)
            mixedManager.getOrCreate(target2)

            mixedManager.destroyAll()

            assertEquals(2, createdClients.size)
            createdClients.forEach { assertEquals(1, it.destroyCallCount) }
        }

    @Test
    fun `destroyAll on empty manager is a no-op`() = testScope.runTest {
        manager.destroyAll()
        // No exception thrown; nothing to assert beyond reaching here
    }

    @Test
    fun `pending setTarget is skipped after destroy key and recreate same key`() =
        testScope.runTest {
            manager.getOrCreate(target1)
            manager.getOrCreate(target1v2) // schedules setTarget(target1v2) on first client

            manager.destroy(key1)
            manager.getOrCreate(target1v2) // creates second client for same key

            testScheduler.advanceUntilIdle()

            assertEquals(2, createdClients.size)
            assertEquals(1, createdClients[0].destroyCallCount)
            assertEquals(0, createdClients[0].setTargetCallCount)
            assertEquals(0, createdClients[1].setTargetCallCount)
        }

    @Test
    fun `pending setTarget is skipped after destroyAll and recreate same key`() =
        testScope.runTest {
            manager.getOrCreate(target1)
            manager.getOrCreate(target1v2) // schedules setTarget(target1v2) on first client

            manager.destroyAll()
            manager.getOrCreate(target1v2) // creates second client for same key

            testScheduler.advanceUntilIdle()

            assertEquals(2, createdClients.size)
            assertEquals(1, createdClients[0].destroyCallCount)
            assertEquals(0, createdClients[0].setTargetCallCount)
            assertEquals(0, createdClients[1].setTargetCallCount)
        }

    // --- Ordering-race regression tests ---

    @Test
    fun `rapid target changes - only the last target is applied to the client`() =
        testScope.runTest {
            val target1v3 = Target(key1, trafficType = "third")

            // Three consecutive calls with the same key but different targets,
            // all before any coroutine has had a chance to execute.
            manager.getOrCreate(target1)    // creates client, no setTarget
            manager.getOrCreate(target1v2)  // schedules setTarget(v2)
            manager.getOrCreate(target1v3)  // schedules setTarget(v3), makes v2 stale

            testScheduler.advanceUntilIdle()

            // The v2 launch must have self-cancelled because lastTargets already held v3.
            // Only one setTarget must have reached the client, and it must be v3.
            assertEquals(1, createdClients[0].setTargetCallCount)
            assertEquals(target1v3, createdClients[0].lastSetTarget)
        }

    @Test
    fun `interleaved calls - intermediate target change does not override final target`() =
        testScope.runTest {
            val target1v3 = Target(key1, trafficType = "third")

            manager.getOrCreate(target1)    // creates client
            manager.getOrCreate(target1v2)  // schedules setTarget(v2)
            manager.getOrCreate(target1v3)  // schedules setTarget(v3), v2 becomes stale
            manager.getOrCreate(target1v3)  // same as v3 - no-op, no new launch

            testScheduler.advanceUntilIdle()

            assertEquals(1, createdClients[0].setTargetCallCount)
            assertEquals(target1v3, createdClients[0].lastSetTarget)
        }

    @Test
    fun `two distinct sequential target changes each apply`() =
        testScope.runTest {
            // First change settles before second is issued.
            manager.getOrCreate(target1)    // creates client
            manager.getOrCreate(target1v2)  // schedules setTarget(v2)
            testScheduler.advanceUntilIdle()

            // v2 should have applied - now switch to a third target.
            val target1v3 = Target(key1, trafficType = "third")
            manager.getOrCreate(target1v3)  // schedules setTarget(v3)
            testScheduler.advanceUntilIdle()

            assertEquals(2, createdClients[0].setTargetCallCount)
            assertEquals(target1v3, createdClients[0].lastSetTarget)
        }
}

private class FakeSplitClient(
    private val destroyShouldThrow: Boolean = false,
) : SplitClient {

    var setTargetCallCount = 0
        private set
    var lastSetTarget: Target? = null
        private set
    var destroyCallCount = 0
        private set

    override fun getTreatment(flag: String, evaluationOptions: EvaluationOptions?): EvaluationResult =
        throw UnsupportedOperationException()

    override fun getTreatments(
        flags: List<String>,
        evaluationOptions: EvaluationOptions?,
    ): List<EvaluationResult> = throw UnsupportedOperationException()

    override fun getTreatmentsByFlagSets(
        flagSets: List<String>,
        evaluationOptions: EvaluationOptions?,
    ): List<EvaluationResult> = throw UnsupportedOperationException()

    override fun setTarget(target: Target) {
        setTargetCallCount++
        lastSetTarget = target
    }

    override fun addEventListener(listener: SplitEventListener): Unit =
        throw UnsupportedOperationException()

    override fun track(
        eventType: String,
        value: Double?,
        properties: Map<String, Any?>?,
    ): Unit = throw UnsupportedOperationException()

    override suspend fun destroy() {
        destroyCallCount++
        if (destroyShouldThrow) throw RuntimeException("destroy failed")
    }

    @Deprecated("Use suspend destroy()", level = DeprecationLevel.ERROR)
    override fun destroyAsync(callback: SplitVoidCallback): Unit =
        throw UnsupportedOperationException()

    override suspend fun flush(): Unit = throw UnsupportedOperationException()

    @Deprecated("Use suspend flush()", level = DeprecationLevel.ERROR)
    override fun flushAsync(callback: SplitVoidCallback): Unit =
        throw UnsupportedOperationException()
}
