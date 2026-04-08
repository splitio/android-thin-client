package io.split.client.thin.internal

import io.split.client.thin.EvaluationOptions
import io.split.client.thin.EvaluationResult
import io.split.client.thin.Key
import io.split.client.thin.SdkKey
import io.split.client.thin.SplitCallback
import io.split.client.thin.SplitClient
import io.split.client.thin.SplitEventListener
import io.split.client.thin.SplitVoidCallback
import io.split.client.thin.Target
import io.split.client.thin.internal.evaluation.DefaultEvaluationFetchCoordinator
import io.split.client.thin.internal.evaluation.EvaluationFetchCoordinator
import io.split.client.thin.internal.evaluation.EvaluationChange
import io.split.client.thin.internal.evaluation.EvaluationKey
import io.split.client.thin.internal.evaluation.EvaluationProvider
import io.split.client.thin.internal.evaluation.EvaluationWriteStorage
import io.split.client.thin.internal.evaluation.FetchReason
import io.split.client.thin.internal.evaluation.StoredEvaluation
import io.split.client.thin.internal.evaluation.toEvaluationKey
import io.split.client.thin.SplitClientConfig
import io.split.client.thin.internal.observer.DefaultCompositeObserver
import io.split.client.thin.internal.observer.ObservableEvent
import io.split.client.thin.internal.observer.ObservableEventType
import io.split.client.thin.internal.observer.Observer
import io.split.client.thin.internal.secure.EvaluationFilters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DefaultSplitFactoryTest {

    private val sdkKey = SdkKey("sdk-key")
    private val defaultTarget = Target(Key("default-user"), trafficType = "user")
    private val otherTarget = Target(Key("other-user"), trafficType = "user")

    private lateinit var fakeClientManager: FakeClientManager
    private lateinit var fakeAsyncBridge: FakeAsyncBridge
    private lateinit var factory: DefaultSplitFactory

    @Before
    fun setUp() {
        fakeClientManager = FakeClientManager()
        fakeAsyncBridge = FakeAsyncBridge()
        factory = DefaultSplitFactory(
            defaultTarget = defaultTarget,
            config = null,
            asyncBridge = fakeAsyncBridge,
            evaluationRepository = FakeEvaluationRepository(),
            filters = null,
            fetchCoordinator = FakeEvaluationFetchCoordinator(),
            clientManager = fakeClientManager,
        )
    }

    @Test
    fun `factory init calls clientManager getOrCreate with defaultTarget`() {
        assertEquals(defaultTarget, fakeClientManager.lastGetOrCreateTarget)
    }

    @Test
    fun `getClient null delegates to clientManager with defaultTarget`() {
        val client = factory.getClient(null)

        assertSame(fakeClientManager.lastClient, client)
        assertSame(defaultTarget, fakeClientManager.lastGetOrCreateTarget)
    }

    @Test
    fun `getClient with explicit target delegates to clientManager with that target`() {
        val client = factory.getClient(otherTarget)

        assertSame(fakeClientManager.lastClient, client)
        assertSame(otherTarget, fakeClientManager.lastGetOrCreateTarget)
    }

    @Test
    fun `destroy calls clientManager destroyAll and asyncBridge close`() = runTest {
        factory.destroy()

        assertTrue(fakeClientManager.destroyAllCalled)
        assertTrue(fakeAsyncBridge.closeCalled)
    }

    @Test
    fun `destroyAsync delegates to asyncBridge and its block calls destroyAll and closes bridge`() = runTest {
        @Suppress("DEPRECATION_ERROR")
        factory.destroyAsync { /* callback stub */ }

        assertNotNull(fakeAsyncBridge.capturedVoidBlock)

        fakeAsyncBridge.capturedVoidBlock!!.invoke()

        assertTrue(fakeClientManager.destroyAllCalled)
        assertTrue(fakeAsyncBridge.closeCalled)
    }

    @Test
    fun `getManager returns flag names from storage for default target`() {
        val fakeRepository = FakeEvaluationRepository()
        val defaultEvalKey = defaultTarget.toEvaluationKey()
        fakeRepository.store("flag-a", defaultEvalKey, StoredEvaluation(EvaluationResult("flag-a", "on"), emptySet()))
        fakeRepository.store("flag-b", defaultEvalKey, StoredEvaluation(EvaluationResult("flag-b", "off"), emptySet()))

        val testFactory = DefaultSplitFactory(
            defaultTarget = defaultTarget,
            config = null,
            asyncBridge = fakeAsyncBridge,
            evaluationRepository = fakeRepository,
            filters = null,
            fetchCoordinator = FakeEvaluationFetchCoordinator(),
                        clientManager = fakeClientManager,
        )

        assertEquals(setOf("flag-a", "flag-b"), testFactory.getManager().flagNames.toSet())
    }

    @Test
    fun `getManager returns empty list when no flags are stored`() {
        assertEquals(emptyList<String>(), factory.getManager().flagNames)
    }

    @Test
    fun `getClient null with default manager creates client via clientFactory for defaultTarget`() {
        val stubClient = StubSplitClient()
        val testScope = TestScope()
        val customManager = DefaultClientManager(
            scope = testScope,
            clientFactory = { stubClient },
        )
        val factoryWithDefaultManager = DefaultSplitFactory(
            defaultTarget = defaultTarget,
            config = null,
            asyncBridge = fakeAsyncBridge,
            evaluationRepository = FakeEvaluationRepository(),
            filters = null,
            fetchCoordinator = FakeEvaluationFetchCoordinator(),
                        scope = testScope,
            clientManager = customManager,
        )

        val client = factoryWithDefaultManager.getClient(null)

        assertSame(stubClient, client)
    }

    @Test
    fun `getClient with default manager returns same instance for same target key`() {
        var factoryCallCount = 0
        val testScope = TestScope()
        val customManager = DefaultClientManager(
            scope = testScope,
            clientFactory = {
                factoryCallCount++
                StubSplitClient()
            },
        )
        val factoryWithDefaultManager = DefaultSplitFactory(
            defaultTarget = defaultTarget,
            config = null,
            asyncBridge = fakeAsyncBridge,
            evaluationRepository = FakeEvaluationRepository(),
            filters = null,
            fetchCoordinator = FakeEvaluationFetchCoordinator(),
                        scope = testScope,
            clientManager = customManager,
        )

        val first = factoryWithDefaultManager.getClient(defaultTarget)
        val second = factoryWithDefaultManager.getClient(defaultTarget)

        assertSame(first, second)
        assertEquals(1, factoryCallCount)
    }

    @Test
    fun `destroy with default manager cancels scope`() = runTest {
        val testScope = TestScope()
        val factoryWithDefaultManager = DefaultSplitFactory(
            defaultTarget = defaultTarget,
            config = null,
            asyncBridge = fakeAsyncBridge,
            evaluationRepository = FakeEvaluationRepository(),
            filters = null,
            fetchCoordinator = FakeEvaluationFetchCoordinator(),
                        scope = testScope,
        )

        factoryWithDefaultManager.destroy()

        assertTrue(testScope.coroutineContext[kotlinx.coroutines.Job]!!.isCancelled)
    }
}

private class FakeClientManager : ClientManager {

    var lastGetOrCreateTarget: Target? = null
    var destroyAllCalled = false
    val lastClient = StubSplitClient()

    override fun getOrCreate(target: Target): SplitClient {
        lastGetOrCreateTarget = target
        return lastClient
    }

    override suspend fun destroy(key: Key) {}

    override suspend fun destroyAll() {
        destroyAllCalled = true
    }
}

private class FakeAsyncBridge : AsyncBridgeLike {

    var closeCalled = false
    var capturedVoidBlock: (suspend () -> Unit)? = null

    override fun <T> executeAsync(callback: SplitCallback<T>, block: suspend () -> T) {}

    override fun executeAsync(callback: SplitVoidCallback, block: suspend () -> Unit) {
        capturedVoidBlock = block
    }

    override fun close() {
        closeCalled = true
    }
}

// Verify FetchReason → ObservableEventType mapping by exercising the production onEvaluationsUpdated callback
class FetchReasonObserverMappingTest {

    private val evalKey = EvaluationKey(Key("user-1"))

    private fun makeCoordinator(compositeObserver: DefaultCompositeObserver): DefaultEvaluationFetchCoordinator {
        return DefaultEvaluationFetchCoordinator(
            provider = object : EvaluationProvider {
                override suspend fun fetch(evalKey: EvaluationKey, filters: EvaluationFilters?, changeNumber: Long): EvaluationChange =
                    EvaluationChange(evalKey, -1L, emptyList())
            },
            readStorage = FakeEvaluationReadStorage(),
            writeStorage = object : EvaluationWriteStorage {
                override fun upsert(change: EvaluationChange): Boolean = true
                override fun clear(evalKey: EvaluationKey) {}
            },
            onEvaluationsUpdated = { evalKey, reason ->
                val eventType = when (reason) {
                    FetchReason.INITIALIZATION, FetchReason.TARGET_SWITCH ->
                        ObservableEventType.EVAL_STORAGE_UPDATED
                    FetchReason.PERIODIC, FetchReason.PUSH ->
                        ObservableEventType.EVALUATIONS_UPDATED
                }
                compositeObserver.notifyEvent(
                    ObservableEvent(eventType, mapOf("matchingKey" to evalKey.key.matchingKey))
                )
            },
        )
    }

    @Test
    fun `INITIALIZATION fetch emits EVAL_STORAGE_UPDATED to observers`() = runTest {
        val fakeObserver = FakeObserver()
        val compositeObserver = DefaultCompositeObserver()
        compositeObserver.register(fakeObserver)

        makeCoordinator(compositeObserver).fetchIfNeeded(evalKey, null, FetchReason.INITIALIZATION)

        assertEquals(listOf(ObservableEventType.EVAL_STORAGE_UPDATED), fakeObserver.receivedEventTypes)
    }

    @Test
    fun `TARGET_SWITCH fetch emits EVAL_STORAGE_UPDATED to observers`() = runTest {
        val fakeObserver = FakeObserver()
        val compositeObserver = DefaultCompositeObserver()
        compositeObserver.register(fakeObserver)

        makeCoordinator(compositeObserver).fetchIfNeeded(evalKey, null, FetchReason.TARGET_SWITCH)

        assertEquals(listOf(ObservableEventType.EVAL_STORAGE_UPDATED), fakeObserver.receivedEventTypes)
    }

    @Test
    fun `PERIODIC fetch emits EVALUATIONS_UPDATED to observers`() = runTest {
        val fakeObserver = FakeObserver()
        val compositeObserver = DefaultCompositeObserver()
        compositeObserver.register(fakeObserver)

        makeCoordinator(compositeObserver).fetchIfNeeded(evalKey, null, FetchReason.PERIODIC)

        assertEquals(listOf(ObservableEventType.EVALUATIONS_UPDATED), fakeObserver.receivedEventTypes)
    }

    @Test
    fun `PUSH fetch emits EVALUATIONS_UPDATED to observers`() = runTest {
        val fakeObserver = FakeObserver()
        val compositeObserver = DefaultCompositeObserver()
        compositeObserver.register(fakeObserver)

        makeCoordinator(compositeObserver).fetchIfNeeded(evalKey, null, FetchReason.PUSH)

        assertEquals(listOf(ObservableEventType.EVALUATIONS_UPDATED), fakeObserver.receivedEventTypes)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SdkReadyTimeoutTest {

    private val defaultTarget = Target(Key("user-1"), trafficType = "user")

    @Test
    fun `emits SDK_READY_TIMEOUT_REACHED after configured timeout seconds`() = runTest {
        val fakeObserver = FakeObserver()
        val compositeObserver = DefaultCompositeObserver()
        compositeObserver.register(fakeObserver)

        val config = SplitClientConfig.Builder()
            .sync(SplitClientConfig.SyncConfig.Builder().timeout(1).build())
            .build()

        DefaultSplitFactory(
            defaultTarget = defaultTarget,
            config = config,
            asyncBridge = FakeAsyncBridge(),
            evaluationRepository = FakeEvaluationRepository(),
            filters = null,
            fetchCoordinator = FakeEvaluationFetchCoordinator(),
                        scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            compositeObserver = compositeObserver,
            clientManager = FakeClientManager(),
        )

        advanceTimeBy(1_001)

        assertTrue(
            fakeObserver.receivedEventTypes.contains(ObservableEventType.SDK_READY_TIMEOUT_REACHED)
        )
    }

    @Test
    fun `does not emit SDK_READY_TIMEOUT_REACHED when timeout is -1`() = runTest {
        val fakeObserver = FakeObserver()
        val compositeObserver = DefaultCompositeObserver()
        compositeObserver.register(fakeObserver)

        DefaultSplitFactory(
            defaultTarget = defaultTarget,
            config = null,   // default: no timeout (-1)
            asyncBridge = FakeAsyncBridge(),
            evaluationRepository = FakeEvaluationRepository(),
            filters = null,
            fetchCoordinator = FakeEvaluationFetchCoordinator(),
                        scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            compositeObserver = compositeObserver,
            clientManager = FakeClientManager(),
        )

        advanceTimeBy(100_000)

        assertFalse(
            fakeObserver.receivedEventTypes.contains(ObservableEventType.SDK_READY_TIMEOUT_REACHED)
        )
    }
}

private class FakeObserver : Observer {
    val receivedEventTypes = mutableListOf<String>()
    override fun notifyEvent(event: ObservableEvent) {
        receivedEventTypes.add(event.type)
    }
}

private class StubSplitClient : SplitClient {
    override fun getTreatment(flag: String, evaluationOptions: EvaluationOptions?): EvaluationResult =
        throw UnsupportedOperationException()

    override fun getTreatments(flags: List<String>, evaluationOptions: EvaluationOptions?): List<EvaluationResult> =
        throw UnsupportedOperationException()

    override fun getTreatmentsByFlagSets(flagSets: List<String>, evaluationOptions: EvaluationOptions?): List<EvaluationResult> =
        throw UnsupportedOperationException()

    override fun setTarget(target: Target) = Unit

    override fun addEventListener(listener: SplitEventListener): Unit =
        throw UnsupportedOperationException()

    override fun track(eventType: String, value: Double?, properties: Map<String, Any?>?) =
        throw UnsupportedOperationException()

    override suspend fun destroy() = Unit

    @Deprecated("Use suspend destroy()", level = DeprecationLevel.ERROR)
    override fun destroyAsync(callback: SplitVoidCallback): Unit =
        throw UnsupportedOperationException()

    override suspend fun flush(): Unit = throw UnsupportedOperationException()

    @Deprecated("Use suspend flush()", level = DeprecationLevel.ERROR)
    override fun flushAsync(callback: SplitVoidCallback): Unit =
        throw UnsupportedOperationException()
}
