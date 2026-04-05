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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
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
            filters = null,
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
            eq(0.0),
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
    fun `track uses 0 dot 0 when value is null`() {
        client.track("purchase", null, null)

        verify(tracker).track(
            eq("user-1"),
            eq("user"),
            eq("purchase"),
            eq(0.0),
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
    fun `setTarget delegates to evaluation repository`() = testScope.runTest {
        val newTarget = Target(Key("user-2"), trafficType = "user")
        client.setTarget(newTarget)
        testScheduler.advanceUntilIdle()

        assertEquals(1, evaluationRepository.setTargetCalls.size)
        assertEquals(newTarget, evaluationRepository.setTargetCalls[0].first)
    }

    @Test
    fun `setTarget skips evaluationRepository when only trafficType changed`() = testScope.runTest {
        val newTarget = Target(Key("user-1"), trafficType = "account")
        client.setTarget(newTarget)
        testScheduler.advanceUntilIdle()

        assertTrue(evaluationRepository.setTargetCalls.isEmpty())
    }

    @Test
    fun `setTarget calls evaluationRepository when key changes`() = testScope.runTest {
        val newTarget = Target(Key("user-2"), trafficType = "user")
        client.setTarget(newTarget)
        testScheduler.advanceUntilIdle()

        assertEquals(1, evaluationRepository.setTargetCalls.size)
        assertEquals(newTarget, evaluationRepository.setTargetCalls[0].first)
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
    fun `track via SplitClient interface with only eventType uses target traffic type`() {
        val splitClient: SplitClient = client
        splitClient.track("purchase")

        verify(tracker).track(
            eq("user-1"),
            eq("user"),
            eq("purchase"),
            eq(0.0),
            eq(null),
            eq(true),
        )
    }

}

// Test fakes

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

    override fun lastChangeNumber(evalKey: EvaluationKey): Long = -1L
}

class FakeEvaluationFetchCoordinator : EvaluationFetchCoordinator {
    override suspend fun fetchIfNeeded(evalKey: EvaluationKey, filters: EvaluationFilters?, reason: FetchReason): Boolean = false
    override suspend fun refetchAll(filters: EvaluationFilters?, reason: FetchReason, delayProvider: ((EvaluationKey) -> Long)?) {}
}

class FakeEvaluationRepository : EvaluationRepository {
    private val storedEvaluations = mutableMapOf<Pair<String, EvaluationKey>, StoredEvaluation>()
    val setTargetCalls = mutableListOf<Pair<Target, EvaluationFilters?>>()

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

    override suspend fun setTarget(target: Target, filters: EvaluationFilters?) {
        setTargetCalls.add(target to filters)
    }

    override fun getFlagNames(evalKey: EvaluationKey): Set<String> =
        storedEvaluations.keys.filter { it.second == evalKey }.map { it.first }.toSet()
}
