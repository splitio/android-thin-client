package io.split.client.thin.internal

import io.harness.events.EventsManager
import io.split.android.client.tracker.Tracker
import io.split.client.thin.EvaluationResult
import io.split.client.thin.Key
import io.split.client.thin.SplitClient
import io.split.client.thin.SplitEvent
import io.split.client.thin.SplitEventListener
import io.split.client.thin.Target
import io.split.client.thin.internal.evaluation.EvaluationFetchCoordinator
import io.split.client.thin.internal.evaluation.EvaluationKey
import io.split.client.thin.internal.evaluation.EvaluationReadStorage
import io.split.client.thin.internal.evaluation.EvaluationRepository
import io.split.client.thin.internal.evaluation.FetchReason
import io.split.client.thin.internal.evaluation.StoredEvaluation
import io.split.client.thin.internal.secure.EvaluationFilters
import io.split.client.thin.internal.sdkevents.SdkInternalEvent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@Suppress("UNCHECKED_CAST")
class DefaultSplitClientTest {

    private val testScope = TestScope()

    private lateinit var tracker: Tracker
    private lateinit var target: Target
    private lateinit var evaluationRepository: FakeEvaluationRepository
    private lateinit var eventsManager: EventsManager<SplitEvent, SdkInternalEvent, Any?>
    private lateinit var client: DefaultSplitClient

    @Before
    fun setUp() {
        tracker = mock(Tracker::class.java)
        target = Target(Key("user-1"), trafficType = "user")
        evaluationRepository = FakeEvaluationRepository()
        eventsManager = mock(EventsManager::class.java) as EventsManager<SplitEvent, SdkInternalEvent, Any?>
        `when`(eventsManager.eventAlreadyTriggered(SplitEvent.SDK_READY)).thenReturn(true)
        client = DefaultSplitClient(
            initialTarget = target,
            tracker = tracker,
            eventsManager = eventsManager,
            evaluationRepository = evaluationRepository,
            filters = EvaluationFilters(),
            fallbackCalculator = null,
            scope = testScope,
        )
    }

    @Test
    fun `track uses default value and properties when omitted`() {
        client.track("purchase")

        verify(tracker).track(
            eq("user-1"),
            eq("user"),
            eq("purchase"),
            eq(null),
            eq(null),
            eq(true),
        )
    }

    @Test
    fun `track delegates to tracker using target traffic type`() {
        client.track("purchase", 9.99, null)

        verify(tracker).track(
            eq("user-1"),
            eq("user"),
            eq("purchase"),
            eq(9.99),
            eq(null),
            eq(true),
        )
    }

    @Test
    fun `track with null value passes null to tracker`() {
        client.track("purchase", null, null)

        verify(tracker).track(
            eq("user-1"),
            eq("user"),
            eq("purchase"),
            eq(null),
            eq(null),
            eq(true),
        )
    }

    @Test
    fun `track passes isSdkReady from events manager`() {
        `when`(eventsManager.eventAlreadyTriggered(SplitEvent.SDK_READY)).thenReturn(false)

        client.track("purchase", 0.0, null)

        verify(tracker).track(
            eq("user-1"),
            eq("user"),
            eq("purchase"),
            eq(0.0),
            eq(null),
            eq(false),
        )
    }

    @Test
    fun `track returns true when tracker accepts the event`() {
        `when`(tracker.track(any(), any(), any(), any(), any(), anyBoolean())).thenReturn(true)

        val result = client.track("purchase")

        assertTrue(result)
    }

    @Test
    fun `track returns false when tracker rejects the event`() {
        `when`(tracker.track(any(), any(), any(), any(), any(), anyBoolean())).thenReturn(false)

        val result = client.track("purchase")

        assertFalse(result)
    }

    @Test
    fun `track with NaN value returns false and does not call tracker`() {
        val result = client.track("purchase", Double.NaN, null)

        assertFalse(result)
        verify(tracker, never()).track(any(), any(), any(), any(), any(), anyBoolean())
    }

    @Test
    fun `track with non-finite property returns false and does not call tracker`() {
        val result = client.track("purchase", 1.0, mapOf("discount" to Double.POSITIVE_INFINITY))

        assertFalse(result)
        verify(tracker, never()).track(any(), any(), any(), any(), any(), anyBoolean())
    }

    @Test
    fun `track returns false when client is destroyed`() = runTest {
        client.destroy()

        val result = client.track("purchase")

        assertFalse(result)
    }

    @Test
    fun `flush is no-op`() = runTest {
        client.flush()
        verify(tracker, never()).enableTracking(false)
        verify(tracker, never()).enableTracking(true)
    }

    @Test
    fun `destroy disables tracking and destroys events manager`() = runTest {
        client.destroy()
        verify(tracker).enableTracking(false)
        verify(eventsManager).destroy()
    }

    @Test
    fun `setTarget updates traffic type used for subsequent track calls`() = runTest {
        val newTarget = Target(Key("user-2"), trafficType = "account")
        client.setTarget(newTarget)

        client.track("purchase", 0.0, null)

        verify(tracker).track(
            eq("user-2"),
            eq("account"),
            eq("purchase"),
            eq(0.0),
            eq(null),
            eq(true),
        )
    }

    @Test
    fun `setTarget skips evaluationRepository when only trafficType changed`() = testScope.runTest {
        val newTarget = Target(Key("user-1"), trafficType = "account")
        client.setTarget(newTarget)
        testScheduler.advanceUntilIdle()

        assertTrue(evaluationRepository.setTargetCalls.isEmpty())
    }

    @Test
    fun `getTreatment returns stored evaluation result`() {
        val evalKey = EvaluationKey(target.key)
        val stored = StoredEvaluation(EvaluationResult("my_flag", "on"))
        evaluationRepository.store("my_flag", evalKey, stored)

        val result = client.getTreatment("my_flag")

        assertEquals("on", result.treatment)
        assertEquals("my_flag", result.flag)
    }

    @Test
    fun `getTreatment returns control when no stored evaluation`() {
        val result = client.getTreatment("unknown_flag")

        assertEquals("control", result.treatment)
        assertEquals("unknown_flag", result.flag)
    }

    @Test
    fun `getTreatments returns results for multiple flags`() {
        val evalKey = EvaluationKey(target.key)
        evaluationRepository.store("flag_a", evalKey, StoredEvaluation(EvaluationResult("flag_a", "on")))
        evaluationRepository.store("flag_b", evalKey, StoredEvaluation(EvaluationResult("flag_b", "off")))

        val results = client.getTreatments(listOf("flag_a", "flag_b", "flag_c"))

        assertEquals(3, results.size)
        assertEquals("on", results.find { it.flag == "flag_a" }?.treatment)
        assertEquals("off", results.find { it.flag == "flag_b" }?.treatment)
        assertEquals("control", results.find { it.flag == "flag_c" }?.treatment)
    }

    @Test
    fun `getTreatmentsByFlagSets returns results matching flag sets`() {
        val evalKey = EvaluationKey(target.key)
        evaluationRepository.store(
            "flag_a", evalKey,
            StoredEvaluation(EvaluationResult("flag_a", "on"), flagSets = setOf("set_1"))
        )
        evaluationRepository.store(
            "flag_b", evalKey,
            StoredEvaluation(EvaluationResult("flag_b", "off"), flagSets = setOf("set_2"))
        )

        val results = client.getTreatmentsByFlagSets(listOf("set_1"))

        assertEquals(1, results.size)
        assertEquals("flag_a", results[0].flag)
        assertEquals("on", results[0].treatment)
    }

    @Test
    fun `getTreatmentsByFlagSets with no configured filter passes through all sets`() {
        val evalKey = EvaluationKey(target.key)
        evaluationRepository.store(
            "flag_a", evalKey,
            StoredEvaluation(EvaluationResult("flag_a", "on"), flagSets = setOf("set_1"))
        )

        val results = client.getTreatmentsByFlagSets(listOf("set_1", "set_2"))

        assertEquals(1, results.size)
        assertEquals("flag_a", results[0].flag)
    }

    @Test
    fun `getTreatmentsByFlagSets with configured filter intersects sets`() {
        val evalKey = EvaluationKey(target.key)
        evaluationRepository.store("flag_a", evalKey, StoredEvaluation(EvaluationResult("flag_a", "on"), flagSets = setOf("set_1")))
        evaluationRepository.store("flag_b", evalKey, StoredEvaluation(EvaluationResult("flag_b", "off"), flagSets = setOf("set_2")))

        val clientWithFilter = DefaultSplitClient(
            initialTarget = target,
            tracker = tracker,
            eventsManager = eventsManager,
            evaluationRepository = evaluationRepository,
            filters = EvaluationFilters(sets = setOf("set_1")),
            fallbackCalculator = null,
            scope = testScope,
        )

        val results = clientWithFilter.getTreatmentsByFlagSets(listOf("set_1", "set_2"))

        assertEquals(1, results.size)
        assertEquals("flag_a", results[0].flag)
    }

    @Test
    fun `getTreatmentsByFlagSets returns empty map when intersection is empty`() {
        val clientWithFilter = DefaultSplitClient(
            initialTarget = target,
            tracker = tracker,
            eventsManager = eventsManager,
            evaluationRepository = evaluationRepository,
            filters = EvaluationFilters(sets = setOf("set_1")),
            fallbackCalculator = null,
            scope = testScope,
        )

        val results = clientWithFilter.getTreatmentsByFlagSets(listOf("set_2", "set_3"))

        assertTrue(results.isEmpty())
    }

    @Test
    fun `getTreatmentsByFlagSets logs warning for sets not in configured filter`() {
        val clientWithFilter = DefaultSplitClient(
            initialTarget = target,
            tracker = tracker,
            eventsManager = eventsManager,
            evaluationRepository = evaluationRepository,
            filters = EvaluationFilters(sets = setOf("set_1")),
            fallbackCalculator = null,
            scope = testScope,
        )

        // Should not throw; set_2 is not in configured filter → warning logged, result empty
        val results = clientWithFilter.getTreatmentsByFlagSets(listOf("set_2"))

        assertTrue(results.isEmpty())
    }

    @Test
    fun `setTarget invokes onTargetChanged with new matchingKey`() = testScope.runTest {
        val capturedTargets = mutableListOf<Target>()
        val clientWithCallback = DefaultSplitClient(
            initialTarget = target,
            tracker = tracker,
            eventsManager = eventsManager,
            evaluationRepository = evaluationRepository,
            filters = EvaluationFilters(),
            fallbackCalculator = null,
            scope = testScope,
            onTargetChanged = { _, newTarget -> capturedTargets.add(newTarget) },
        )

        val newTarget = Target(Key("user-2"), trafficType = "user")
        clientWithCallback.setTarget(newTarget)

        assertEquals(listOf(newTarget), capturedTargets)
    }

    @Test
    fun `setTarget does not invoke onTargetChanged when evalKey unchanged`() = testScope.runTest {
        val capturedCalls = mutableListOf<Pair<EvaluationKey, Target>>()
        val clientWithCallback = DefaultSplitClient(
            initialTarget = target,
            tracker = tracker,
            eventsManager = eventsManager,
            evaluationRepository = evaluationRepository,
            filters = EvaluationFilters(),
            fallbackCalculator = null,
            scope = testScope,
            onTargetChanged = { oldEvalKey, newTarget -> capturedCalls.add(oldEvalKey to newTarget) },
        )

        clientWithCallback.setTarget(Target(Key("user-1"), trafficType = "account"))

        assertTrue(capturedCalls.isEmpty())
    }

    @Test
    fun `addEventListener registers all event handlers with events manager`() {
        val listener = object : SplitEventListener() {}

        client.addEventListener(listener)

        verify(eventsManager).register(eq(SplitEvent.SDK_READY), any())
        verify(eventsManager).register(eq(SplitEvent.SDK_READY_FROM_CACHE), any())
        verify(eventsManager).register(eq(SplitEvent.SDK_READY_TIMEOUT), any())
        verify(eventsManager).register(eq(SplitEvent.SDK_UPDATE), any())
    }

    @Test
    fun `destroy does not throw`() = runTest {
        client.destroy()
    }

    @Test
    fun `destroy invokes destroyOperation when provided`() = runTest {
        var opCalled = false
        val clientWithOp = DefaultSplitClient(
            initialTarget = target,
            tracker = tracker,
            eventsManager = eventsManager,
            evaluationRepository = evaluationRepository,
            filters = EvaluationFilters(),
            fallbackCalculator = null,
            scope = testScope,
            destroyOperation = { opCalled = true },
        )

        clientWithOp.destroy()

        assertTrue(opCalled)
        // internal teardown should NOT have run since destroyOperation was provided
        verify(tracker, never()).enableTracking(false)
        verify(eventsManager, never()).destroy()
    }

    @Test
    fun `destroy falls back to internal teardown when destroyOperation is null`() = runTest {
        // Default client has no destroyOperation, so internal teardown runs
        client.destroy()
        verify(tracker).enableTracking(false)
        verify(eventsManager).destroy()
    }

    @Test
    fun `tearDownInternal runs flush, disables tracking, destroys eventsManager, and calls releaseResources`() = runTest {
        var releaseCalled = false
        val clientWithRelease = DefaultSplitClient(
            initialTarget = target,
            tracker = tracker,
            eventsManager = eventsManager,
            evaluationRepository = evaluationRepository,
            filters = EvaluationFilters(),
            fallbackCalculator = null,
            scope = testScope,
            releaseResources = { releaseCalled = true },
        )

        (clientWithRelease as InternalDestroyable).tearDownInternal()

        verify(tracker).enableTracking(false)
        verify(eventsManager).destroy()
        assertTrue(releaseCalled)
    }

    @Test
    fun `setTarget with blank matchingKey is a no-op`() = testScope.runTest {
        val blankTarget = Target(Key(""), trafficType = "user")
        val clientWithRejectingValidator = DefaultSplitClient(
            initialTarget = target,
            tracker = tracker,
            eventsManager = eventsManager,
            evaluationRepository = evaluationRepository,
            filters = EvaluationFilters(),
            fallbackCalculator = null,
            scope = testScope,
            inputValidator = FakeInputValidator(valid = false),
        )
        clientWithRejectingValidator.setTarget(blankTarget)
        testScheduler.advanceUntilIdle()

        assertTrue(evaluationRepository.setTargetCalls.isEmpty())
    }

    @Test
    fun `track via SplitClient interface with only eventType uses target traffic type`() {
        val splitClient: SplitClient = client
        splitClient.track("purchase")

        verify(tracker).track(
            eq("user-1"),
            eq("user"),
            eq("purchase"),
            eq(null),
            eq(null),
            eq(true),
        )
    }

    @Test
    fun `setTarget with new eval key invokes onTargetChanged with oldEvalKey and newTarget`() = testScope.runTest {
        val capturedCalls = mutableListOf<Pair<EvaluationKey, Target>>()
        val clientWithCallback = DefaultSplitClient(
            initialTarget = target,
            tracker = tracker,
            eventsManager = eventsManager,
            evaluationRepository = evaluationRepository,
            filters = EvaluationFilters(),
            fallbackCalculator = null,
            scope = testScope,
            onTargetChanged = { oldEvalKey, newTarget -> capturedCalls.add(oldEvalKey to newTarget) },
        )

        val newTarget = Target(Key("user-2"), trafficType = "user")
        clientWithCallback.setTarget(newTarget)

        assertEquals(1, capturedCalls.size)
        assertEquals(EvaluationKey(Key("user-1")), capturedCalls[0].first)
        assertEquals(newTarget, capturedCalls[0].second)
    }

    @Test
    fun `setTarget with same eval key does not invoke onTargetChanged`() = testScope.runTest {
        val capturedCalls = mutableListOf<Pair<EvaluationKey, Target>>()
        val clientWithCallback = DefaultSplitClient(
            initialTarget = target,
            tracker = tracker,
            eventsManager = eventsManager,
            evaluationRepository = evaluationRepository,
            filters = EvaluationFilters(),
            fallbackCalculator = null,
            scope = testScope,
            onTargetChanged = { oldEvalKey, newTarget -> capturedCalls.add(oldEvalKey to newTarget) },
        )

        clientWithCallback.setTarget(Target(Key("user-1"), trafficType = "account"))

        assertTrue(capturedCalls.isEmpty())
    }

    @Test
    fun `setTarget validation failure does not invoke onTargetChanged`() = testScope.runTest {
        val capturedCalls = mutableListOf<Pair<EvaluationKey, Target>>()
        val clientWithCallback = DefaultSplitClient(
            initialTarget = target,
            tracker = tracker,
            eventsManager = eventsManager,
            evaluationRepository = evaluationRepository,
            filters = EvaluationFilters(),
            fallbackCalculator = null,
            scope = testScope,
            onTargetChanged = { oldEvalKey, newTarget -> capturedCalls.add(oldEvalKey to newTarget) },
            inputValidator = FakeInputValidator(valid = false),
        )

        clientWithCallback.setTarget(Target(Key("user-2"), trafficType = "user"))

        assertTrue(capturedCalls.isEmpty())
    }

    @Test
    fun `getTreatment after destroy returns control`() = runTest {
        val evalKey = EvaluationKey(target.key)
        evaluationRepository.store("my_flag", evalKey, StoredEvaluation(EvaluationResult("my_flag", "on")))

        (client as InternalDestroyable).tearDownInternal()

        assertEquals("control", client.getTreatment("my_flag").treatment)
    }

    @Test
    fun `getTreatments after destroy returns control for all flags`() = runTest {
        val evalKey = EvaluationKey(target.key)
        evaluationRepository.store("flag_a", evalKey, StoredEvaluation(EvaluationResult("flag_a", "on")))

        (client as InternalDestroyable).tearDownInternal()

        val results = client.getTreatments(listOf("flag_a", "flag_b"))
        assertEquals(2, results.size)
        assertTrue(results.all { it.treatment == "control" })
    }

    @Test
    fun `getTreatmentsByFlagSets after destroy returns empty`() = runTest {
        val evalKey = EvaluationKey(target.key)
        evaluationRepository.store(
            "flag_a", evalKey,
            StoredEvaluation(EvaluationResult("flag_a", "on"), flagSets = setOf("set_1"))
        )

        (client as InternalDestroyable).tearDownInternal()

        assertTrue(client.getTreatmentsByFlagSets(listOf("set_1")).isEmpty())
    }

    @Test
    fun `getTreatment after destroy returns configured fallback`() = runTest {
        val fallbackCalculator = mock(io.split.android.client.fallback.FallbackTreatmentsCalculator::class.java)
        `when`(fallbackCalculator.resolve("my_flag"))
            .thenReturn(io.split.android.client.fallback.FallbackTreatment("fb", null))
        val clientWithFallback = DefaultSplitClient(
            initialTarget = target,
            tracker = tracker,
            eventsManager = eventsManager,
            evaluationRepository = evaluationRepository,
            filters = EvaluationFilters(),
            fallbackCalculator = fallbackCalculator,
            scope = testScope,
        )

        (clientWithFallback as InternalDestroyable).tearDownInternal()

        assertEquals("fb", clientWithFallback.getTreatment("my_flag").treatment)
    }

    @Test
    fun `setTarget does not call evaluationRepository dot setTarget directly`() = testScope.runTest {
        val newTarget = Target(Key("user-2"), trafficType = "user")
        client.setTarget(newTarget)
        testScheduler.advanceUntilIdle()

        assertTrue("evaluationRepository.setTarget should not be called directly from DefaultSplitClient",
            evaluationRepository.setTargetCalls.isEmpty())
    }

}

// Test fakes

class FakeInputValidator(private val valid: Boolean) : InputValidator {
    override fun validateSdkKey(sdkKey: io.split.client.thin.SdkKey): Boolean = valid
    override fun validateKey(key: io.split.client.thin.Key): Boolean = valid
    override fun validateFlagName(flagName: String): Boolean = valid
    override fun validateEventValue(value: Double?, properties: Map<String, Any?>?): Boolean = valid
}

class FakeEvaluationReadStorage : EvaluationReadStorage {
    private val storedEvaluations = mutableMapOf<Pair<String, EvaluationKey>, StoredEvaluation>()

    fun store(flag: String, evalKey: EvaluationKey, stored: StoredEvaluation) {
        storedEvaluations[flag to evalKey] = stored
    }

    override fun get(flag: String, evalKey: EvaluationKey): StoredEvaluation? =
        storedEvaluations[flag to evalKey]

    override fun get(flags: Set<String>, evalKey: EvaluationKey): Map<String, StoredEvaluation> =
        flags.mapNotNull { flag -> storedEvaluations[flag to evalKey]?.let { flag to it } }.toMap()

    override fun getByFlagSets(flagSets: Set<String>, evalKey: EvaluationKey): Map<String, StoredEvaluation> =
        storedEvaluations
            .filter { (key, stored) -> key.second == evalKey && stored.flagSets.any { it in flagSets } }
            .mapKeys { it.key.first }

    override fun getFlagNames(evalKey: EvaluationKey): Set<String> =
        storedEvaluations.keys.filter { it.second == evalKey }.map { it.first }.toSet()

    override fun getFlagNames(): Set<String> =
        storedEvaluations.keys.map { it.first }.toSet()

    override fun lastChangeNumber(evalKey: EvaluationKey): Long = -1L
    override fun lastUpdateTimestamp(evalKey: EvaluationKey): Long? = null
}

class FakeEvaluationFetchCoordinator : EvaluationFetchCoordinator {
    override fun fetchedKeys(): Set<EvaluationKey> = emptySet()
    override suspend fun fetchIfNeeded(evalKey: EvaluationKey, filters: EvaluationFilters, reason: FetchReason, delayMs: Long, targetChangeNumber: Long?): Boolean = false
    override suspend fun refetchAll(filters: EvaluationFilters, reason: FetchReason, delayProvider: ((EvaluationKey) -> Long)?, keyFilter: (EvaluationKey) -> Boolean) {}
    override fun forget(evalKey: EvaluationKey) {}
}

class FakeEvaluationRepository : EvaluationRepository {
    private val storedEvaluations = mutableMapOf<Pair<String, EvaluationKey>, StoredEvaluation>()
    val setTargetCalls = mutableListOf<Pair<Target, EvaluationFilters>>()

    fun store(flag: String, evalKey: EvaluationKey, stored: StoredEvaluation) {
        storedEvaluations[flag to evalKey] = stored
    }

    override fun getTreatment(evalKey: EvaluationKey, flag: String): StoredEvaluation? =
        storedEvaluations[flag to evalKey]

    override fun getTreatments(evalKey: EvaluationKey, flags: Set<String>): Map<String, StoredEvaluation> =
        flags.mapNotNull { flag -> storedEvaluations[flag to evalKey]?.let { flag to it } }.toMap()

    override fun getTreatmentsByFlagSets(evalKey: EvaluationKey, flagSets: Set<String>): Map<String, StoredEvaluation> =
        storedEvaluations
            .filter { (key, stored) -> key.second == evalKey && stored.flagSets.any { it in flagSets } }
            .mapKeys { it.key.first }

    override suspend fun setTarget(target: Target, filters: EvaluationFilters, isInitialization: Boolean) {
        setTargetCalls.add(target to filters)
    }

    override fun getFlagNames(evalKey: EvaluationKey): Set<String> =
        storedEvaluations.keys.filter { it.second == evalKey }.map { it.first }.toSet()

    override fun getFlagNames(): Set<String> =
        storedEvaluations.keys.map { it.first }.toSet()
}
