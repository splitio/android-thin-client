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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DefaultSplitFactoryTest {

    private val sdkKey = SdkKey("sdk-key")
    private val defaultTarget = Target(Key("default-user"))
    private val otherTarget = Target(Key("other-user"))

    private lateinit var fakeClientManager: FakeClientManager
    private lateinit var fakeAsyncBridge: FakeAsyncBridge
    private lateinit var factory: DefaultSplitFactory

    @Before
    fun setUp() {
        fakeClientManager = FakeClientManager()
        fakeAsyncBridge = FakeAsyncBridge()
        factory = DefaultSplitFactory(
            sdkKey = sdkKey,
            defaultTarget = defaultTarget,
            config = null,
            asyncBridge = fakeAsyncBridge,
            clientManager = fakeClientManager,
        )
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
    fun `getManager returns default manager with hardcoded names`() {
        val manager = factory.getManager()

        assertEquals(listOf("hardcoded-flag-1", "hardcoded-flag-2"), manager.flagNames)
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
            sdkKey = sdkKey,
            defaultTarget = defaultTarget,
            config = null,
            asyncBridge = fakeAsyncBridge,
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
            sdkKey = sdkKey,
            defaultTarget = defaultTarget,
            config = null,
            asyncBridge = fakeAsyncBridge,
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
            sdkKey = sdkKey,
            defaultTarget = defaultTarget,
            config = null,
            asyncBridge = fakeAsyncBridge,
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

private class StubSplitClient : SplitClient {
    override fun getTreatment(flag: String, evaluationOptions: EvaluationOptions?): EvaluationResult =
        throw UnsupportedOperationException()

    override fun getTreatments(flags: List<String>, evaluationOptions: EvaluationOptions?): List<EvaluationResult> =
        throw UnsupportedOperationException()

    override fun getTreatmentsByFlagSets(flagSets: List<String>, evaluationOptions: EvaluationOptions?): List<EvaluationResult> =
        throw UnsupportedOperationException()

    override suspend fun setTarget(target: Target) = Unit

    @Deprecated("Use suspend setTarget()", level = DeprecationLevel.ERROR)
    override fun setTargetAsync(target: Target, callback: SplitVoidCallback): Unit =
        throw UnsupportedOperationException()

    override fun addEventListener(listener: SplitEventListener): Unit =
        throw UnsupportedOperationException()

    override fun track(trafficType: String, eventType: String, value: Double?, properties: Map<String, Any?>?) =
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
