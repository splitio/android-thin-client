package io.split.client.thin.internal

import io.harness.events.EventHandler
import io.harness.events.EventsManager
import io.split.client.thin.Key
import io.split.client.thin.SplitEvent
import io.split.client.thin.internal.secure.EvaluationFilters
import io.split.client.thin.Target
import io.split.client.thin.internal.auth.AuthProvider
import io.split.client.thin.internal.auth.JwtCredential
import io.split.client.thin.internal.evaluation.EvaluationFetchCoordinator
import io.split.client.thin.internal.evaluation.EvaluationKey
import io.split.client.thin.internal.evaluation.EvaluationWriteStorage
import io.split.client.thin.internal.evaluation.FetchReason
import io.split.client.thin.internal.observer.CompositeObserver
import io.split.client.thin.internal.observer.ObservableEvent
import io.split.client.thin.internal.observer.Observer
import io.split.client.thin.internal.sdkevents.SdkInternalEvent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultClientFactoryTest {

    @Test
    fun `deregister is called with registration key when client destroy is invoked`() = runTest {
        val deregisteredKeys = mutableListOf<io.split.client.thin.Key>()
        val factory = DefaultClientFactory(
            FakeCompositeObserver(),
            this,
            evaluationRepository = FakeEvaluationRepository(),
            filters = EvaluationFilters(),
            fallbackCalculator = null,
            deregister = { key -> deregisteredKeys.add(key) },
        )

        val target = Target(Key("user-1"), trafficType = "user")
        val client = factory(target)
        advanceUntilIdle()
        client.destroy()

        assertEquals(listOf(Key("user-1")), deregisteredKeys)
    }

    @Test
    fun `destroying client forgets its current target in the fetch coordinator`() = runTest {
        val coordinator = FakeFetchCoordinator()
        val target = Target(Key("user-1"), trafficType = "user")
        val factory = DefaultClientFactory(
            FakeCompositeObserver(),
            this,
            evaluationRepository = FakeEvaluationRepository(),
            filters = EvaluationFilters(),
            fallbackCalculator = null,
            fetchCoordinator = coordinator,
        )

        val client = factory(target)
        advanceUntilIdle()
        (client as InternalDestroyable).tearDownInternal()

        assertEquals(listOf(EvaluationKey(target.key)), coordinator.forgottenKeys)
    }

    @Test
    fun `destroying client unregisters its EventManagerObserver from composite observer`() = runTest {
        val composite = FakeCompositeObserver()
        val factory = DefaultClientFactory(
            composite,
            this,
            evaluationRepository = FakeEvaluationRepository(),
            filters = EvaluationFilters(),
            fallbackCalculator = null,
        )

        val target = Target(Key("user-1"), trafficType = "user")
        val client = factory(target)
        advanceUntilIdle()

        assertEquals(1, composite.registeredObservers.size)
        (client as InternalDestroyable).tearDownInternal()
        assertEquals(0, composite.registeredObservers.size)
    }

    @Test
    fun `invoke creates DefaultSplitClient`() {
        val factory = DefaultClientFactory(
            FakeCompositeObserver(),
            TestScope(),
            evaluationRepository = FakeEvaluationRepository(),
            filters = EvaluationFilters(),
            fallbackCalculator = null,
        )

        val client = factory(Target(Key("user-1"), trafficType = "user"))

        assertTrue(client is DefaultSplitClient)
    }

    @Test
    fun `invoke registers one Observer with composite observer`() {
        val compositeObserver = FakeCompositeObserver()
        val factory = DefaultClientFactory(
            compositeObserver,
            TestScope(),
            evaluationRepository = FakeEvaluationRepository(),
            filters = EvaluationFilters(),
            fallbackCalculator = null,
        )

        factory(Target(Key("user-1"), trafficType = "user"))

        assertEquals(1, compositeObserver.registeredObservers.size)
    }

    @Test
    fun `invoke triggers setTarget on evaluationRepository`() = kotlinx.coroutines.test.runTest {
        val repository = FakeEvaluationRepository()
        val target = Target(Key("user-1"), trafficType = "user")
        val factory = DefaultClientFactory(
            FakeCompositeObserver(),
            this,
            evaluationRepository = repository,
            filters = EvaluationFilters(),
            fallbackCalculator = null,
        )

        factory(target)
        advanceUntilIdle()

        assertEquals(1, repository.setTargetCalls.size)
        assertEquals(target, repository.setTargetCalls[0].first)
    }

    @Test
    fun `onTargetChanged with new matchingKey triggers auth ops, forgets old key, and refetches`() = runTest {
        val repository = FakeEvaluationRepository()
        val auth = FakeAuthProvider()
        val coordinator = FakeFetchCoordinator()
        val storage = FakeWriteStorage()
        val oldTarget = Target(Key("user-1"), trafficType = "user")
        val newTarget = Target(Key("user-2"), trafficType = "user")
        val factory = DefaultClientFactory(
            FakeCompositeObserver(),
            this,
            evaluationRepository = repository,
            filters = EvaluationFilters(),
            fallbackCalculator = null,
            authProvider = auth,
            fetchCoordinator = coordinator,
            evaluationStorage = storage,
        )

        val client = factory(oldTarget)
        advanceUntilIdle()
        repository.setTargetCalls.clear()

        client.setTarget(newTarget)
        advanceUntilIdle()

        assertEquals(listOf("user-2"), auth.addTargetCalls)
        assertEquals(1, auth.invalidateAllCount)
        assertEquals(listOf("user-1"), auth.removeTargetCalls)
        assertEquals(1, coordinator.forgottenKeys.size)
        assertEquals(EvaluationKey(oldTarget.key), coordinator.forgottenKeys[0])
        assertEquals(1, storage.clearedKeys.size)
        assertEquals(EvaluationKey(oldTarget.key), storage.clearedKeys[0])
        assertEquals(1, repository.setTargetCalls.size)
        assertEquals(newTarget, repository.setTargetCalls[0].first)
    }

    // Bug fix: setTarget before SDK_READY fires → the new fetch should use isInitialization=true
    // so that EVALUATIONS_SYNC_COMPLETE fires for the new key and SDK_READY can fire.
    @Test
    fun `setTarget before SDK_READY fires uses isInitialization=true for the new fetch`() = runTest {
        val repository = FakeEvaluationRepositoryWithInit()
        val oldTarget = Target(Key("user-1"), trafficType = "user")
        val newTarget = Target(Key("user-2"), trafficType = "user")
        val fakeEventsManager = FakeEventsManager(sdkReadyAlreadyFired = false)
        val factory = DefaultClientFactory(
            FakeCompositeObserver(),
            this,
            evaluationRepository = repository,
            filters = EvaluationFilters(),
            fallbackCalculator = null,
            eventsManagerFactory = { fakeEventsManager },
        )

        val client = factory(oldTarget)
        advanceUntilIdle()
        repository.setTargetCalls.clear()

        client.setTarget(newTarget)
        advanceUntilIdle()

        assertEquals(1, repository.setTargetCalls.size)
        assertTrue(
            "setTarget before SDK_READY should pass isInitialization=true",
            repository.setTargetCalls[0].isInitialization,
        )
    }

    @Test
    fun `setTarget after SDK_READY fires uses isInitialization=false for the new fetch`() = runTest {
        val repository = FakeEvaluationRepositoryWithInit()
        val oldTarget = Target(Key("user-1"), trafficType = "user")
        val newTarget = Target(Key("user-2"), trafficType = "user")
        val fakeEventsManager = FakeEventsManager(sdkReadyAlreadyFired = true)
        val factory = DefaultClientFactory(
            FakeCompositeObserver(),
            this,
            evaluationRepository = repository,
            filters = EvaluationFilters(),
            fallbackCalculator = null,
            eventsManagerFactory = { fakeEventsManager },
        )

        val client = factory(oldTarget)
        advanceUntilIdle()
        repository.setTargetCalls.clear()

        client.setTarget(newTarget)
        advanceUntilIdle()

        assertEquals(1, repository.setTargetCalls.size)
        assertFalse(
            "setTarget after SDK_READY should pass isInitialization=false",
            repository.setTargetCalls[0].isInitialization,
        )
    }

    @Test
    fun `onTargetChanged with same matchingKey skips auth ops but evicts old eval key and refetches`() = runTest {
        val repository = FakeEvaluationRepository()
        val auth = FakeAuthProvider()
        val coordinator = FakeFetchCoordinator()
        val storage = FakeWriteStorage()
        val oldTarget = Target(Key("user-1"), mapOf("plan" to "free"), trafficType = "user")
        val newTarget = Target(Key("user-1"), mapOf("plan" to "premium"), trafficType = "user")
        val factory = DefaultClientFactory(
            FakeCompositeObserver(),
            this,
            evaluationRepository = repository,
            filters = EvaluationFilters(),
            fallbackCalculator = null,
            authProvider = auth,
            fetchCoordinator = coordinator,
            evaluationStorage = storage,
        )

        val client = factory(oldTarget)
        advanceUntilIdle()
        repository.setTargetCalls.clear()

        client.setTarget(newTarget)
        advanceUntilIdle()

        assertTrue("auth.addTarget should not be called for same matchingKey", auth.addTargetCalls.isEmpty())
        assertEquals(0, auth.invalidateAllCount)
        assertTrue("auth.removeTarget should not be called for same matchingKey", auth.removeTargetCalls.isEmpty())
        assertEquals(1, coordinator.forgottenKeys.size)
        assertEquals(1, storage.clearedKeys.size)
        assertEquals(1, repository.setTargetCalls.size)
        assertEquals(newTarget, repository.setTargetCalls[0].first)
    }

    @Test
    fun `destroying client clears its evaluation key from in-memory storage`() = runTest {
        val storage = FakeWriteStorage()
        val coordinator = FakeFetchCoordinator()
        val target = Target(Key("user-1"), trafficType = "user")
        val factory = DefaultClientFactory(
            FakeCompositeObserver(),
            this,
            evaluationRepository = FakeEvaluationRepository(),
            filters = EvaluationFilters(),
            fallbackCalculator = null,
            fetchCoordinator = coordinator,
            evaluationStorage = storage,
        )

        val client = factory(target)
        advanceUntilIdle()

        (client as InternalDestroyable).tearDownInternal()

        assertEquals(listOf(EvaluationKey(target.key)), storage.clearedKeys)
    }

}

private class FakeAuthProvider : AuthProvider {
    val addTargetCalls = mutableListOf<String>()
    val removeTargetCalls = mutableListOf<String>()
    var invalidateAllCount = 0
    var addTargetReturns = true

    override fun addTarget(target: String): Boolean {
        addTargetCalls.add(target)
        return addTargetReturns
    }
    override fun removeTarget(target: String): Boolean {
        removeTargetCalls.add(target)
        return false
    }
    override suspend fun credential(): JwtCredential = JwtCredential("tok", 0L, false, 0)
    override suspend fun credential(targets: Set<String>): JwtCredential = credential()
    override suspend fun invalidateAll() { invalidateAllCount++ }
}

private class FakeFetchCoordinator : EvaluationFetchCoordinator {
    val forgottenKeys = mutableListOf<EvaluationKey>()
    override fun fetchedKeys(): Set<EvaluationKey> = emptySet()
    override suspend fun fetchIfNeeded(evalKey: EvaluationKey, filters: io.split.client.thin.internal.secure.EvaluationFilters, reason: FetchReason, delayMs: Long, targetChangeNumber: Long?) = false
    override suspend fun refetchAll(filters: io.split.client.thin.internal.secure.EvaluationFilters, reason: FetchReason, delayProvider: ((EvaluationKey) -> Long)?, keyFilter: (EvaluationKey) -> Boolean) {}
    override fun forget(evalKey: EvaluationKey) { forgottenKeys.add(evalKey) }
}

private class FakeWriteStorage : EvaluationWriteStorage {
    val clearedKeys = mutableListOf<EvaluationKey>()
    override fun upsert(change: io.split.client.thin.internal.evaluation.EvaluationChange): io.split.client.thin.internal.evaluation.UpsertResult = io.split.client.thin.internal.evaluation.UpsertResult(false, emptyList())
    override fun clear(evalKey: EvaluationKey) { clearedKeys.add(evalKey) }
}

private class FakeEventsManager(
    private val sdkReadyAlreadyFired: Boolean,
) : EventsManager<SplitEvent, SdkInternalEvent, Any?> {
    override fun register(event: SplitEvent, handler: EventHandler<SplitEvent, Any?>) {}
    override fun unregister(event: SplitEvent) {}
    override fun notifyInternalEvent(event: SdkInternalEvent, metadata: Any?) {}
    override fun eventAlreadyTriggered(event: SplitEvent): Boolean =
        event == SplitEvent.SDK_READY && sdkReadyAlreadyFired
    override fun destroy() {}
}

private data class SetTargetCall(val target: Target, val isInitialization: Boolean)

private class FakeEvaluationRepositoryWithInit : io.split.client.thin.internal.evaluation.EvaluationRepository {
    val setTargetCalls = mutableListOf<SetTargetCall>()

    override fun getTreatment(evalKey: EvaluationKey, flag: String) = null
    override fun getTreatments(evalKey: EvaluationKey, flags: Set<String>) = emptyMap<String, io.split.client.thin.internal.evaluation.StoredEvaluation>()
    override fun getTreatmentsByFlagSets(evalKey: EvaluationKey, flagSets: Set<String>) = emptyMap<String, io.split.client.thin.internal.evaluation.StoredEvaluation>()
    override suspend fun setTarget(target: Target, filters: EvaluationFilters, isInitialization: Boolean) {
        setTargetCalls.add(SetTargetCall(target, isInitialization))
    }
    override fun getFlagNames(evalKey: EvaluationKey): Set<String> = emptySet()
    override fun getFlagNames(): Set<String> = emptySet()
}

private class FakeCompositeObserver : CompositeObserver {
    val registeredObservers = mutableListOf<Observer>()

    override fun register(observer: Observer) {
        registeredObservers.add(observer)
    }

    override fun unregister(observer: Observer) {
        registeredObservers.remove(observer)
    }

    override fun unregisterAll() {
        registeredObservers.clear()
    }

    override fun notifyEvent(event: ObservableEvent) {
        registeredObservers.forEach { it.notifyEvent(event) }
    }
}

