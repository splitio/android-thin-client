package io.split.client.thin.internal

import io.harness.events.EventsManager
import io.split.android.client.tracker.Tracker
import io.split.client.thin.EvaluationResult
import io.split.client.thin.Key
import io.split.client.thin.SplitEvent
import io.split.client.thin.SplitEventListener
import io.split.client.thin.Target
import io.split.client.thin.internal.evaluation.EvaluationKey
import io.split.client.thin.internal.evaluation.EvaluationReadStorage
import io.split.client.thin.internal.evaluation.EvaluationRepository
import io.split.client.thin.internal.evaluation.StoredEvaluation
import io.split.client.thin.internal.secure.EvaluationFilters
import io.split.client.thin.internal.sdkevents.SdkInternalEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    private lateinit var tracker: Tracker
    private lateinit var target: Target
    private lateinit var readStorage: FakeEvaluationReadStorage
    private lateinit var evaluationRepository: FakeEvaluationRepository
    private lateinit var eventsManager: EventsManager<SplitEvent, SdkInternalEvent, Any?>
    private lateinit var client: DefaultSplitClient

    @Before
    fun setUp() {
        tracker = mock(Tracker::class.java)
        target = Target(Key("user-1"))
        readStorage = FakeEvaluationReadStorage()
        evaluationRepository = FakeEvaluationRepository()
        eventsManager = mock(EventsManager::class.java) as EventsManager<SplitEvent, SdkInternalEvent, Any?>
        `when`(eventsManager.eventAlreadyTriggered(SplitEvent.SDK_READY)).thenReturn(true)
        client = DefaultSplitClient(
            initialTarget = target,
            tracker = tracker,
            eventsManager = eventsManager,
            readStorage = readStorage,
            evaluationRepository = evaluationRepository,
            filters = null,
            fallbackCalculator = null,
        )
    }

    @Test
    fun `track delegates to tracker with matching key`() {
        client.track("user", "purchase", 9.99, null)

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
        client.track("user", "purchase", null, null)

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

        client.track("user", "purchase", 0.0, null)

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
    fun `setTarget updates key used for subsequent track calls`() = runTest {
        val newTarget = Target(Key("user-2"))
        client.setTarget(newTarget)

        client.track("user", "purchase", 0.0, null)

        verify(tracker).track(
            eq("user-2"),
            eq("user"),
            eq("purchase"),
            eq(0.0),
            eq(null),
            eq(true),
        )
    }

    @Test
    fun `setTarget delegates to evaluation repository`() = runTest {
        val newTarget = Target(Key("user-2"))
        client.setTarget(newTarget)

        assertEquals(1, evaluationRepository.setTargetCalls.size)
        assertEquals(newTarget, evaluationRepository.setTargetCalls[0].first)
    }

    @Test
    fun `getTreatment returns stored evaluation result`() {
        val evalKey = EvaluationKey(target.key)
        val stored = StoredEvaluation(EvaluationResult("my_flag", "on"))
        readStorage.store("my_flag", evalKey, stored)

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
        readStorage.store("flag_a", evalKey, StoredEvaluation(EvaluationResult("flag_a", "on")))
        readStorage.store("flag_b", evalKey, StoredEvaluation(EvaluationResult("flag_b", "off")))

        val results = client.getTreatments(listOf("flag_a", "flag_b", "flag_c"))

        assertEquals(3, results.size)
        assertEquals("on", results.find { it.flag == "flag_a" }?.treatment)
        assertEquals("off", results.find { it.flag == "flag_b" }?.treatment)
        assertEquals("control", results.find { it.flag == "flag_c" }?.treatment)
    }

    @Test
    fun `getTreatmentsByFlagSets returns results matching flag sets`() {
        val evalKey = EvaluationKey(target.key)
        readStorage.store(
            "flag_a", evalKey,
            StoredEvaluation(EvaluationResult("flag_a", "on"), flagSets = setOf("set_1"))
        )
        readStorage.store(
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

class FakeEvaluationRepository : EvaluationRepository {
    val setTargetCalls = mutableListOf<Pair<Target, EvaluationFilters?>>()

    override suspend fun getTreatment(evalKey: EvaluationKey, flag: String): StoredEvaluation? = null
    override suspend fun getTreatments(evalKey: EvaluationKey, flags: Set<String>): Map<String, StoredEvaluation> = emptyMap()
    override suspend fun getTreatmentsByFlagSets(evalKey: EvaluationKey, flagSets: Set<String>): Map<String, StoredEvaluation> = emptyMap()

    override suspend fun setTarget(target: Target, filters: EvaluationFilters?) {
        setTargetCalls.add(target to filters)
    }
}
