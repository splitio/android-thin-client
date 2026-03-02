package io.split.client.thin.internal

import io.split.client.thin.Key
import io.split.client.thin.SdkKey
import io.split.client.thin.SplitCallback
import io.split.client.thin.SplitClient
import io.split.client.thin.SplitVoidCallback
import io.split.client.thin.Target
import kotlinx.coroutines.test.runTest
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

        // Execute the captured block to verify the full destroy chain fires
        fakeAsyncBridge.capturedVoidBlock!!.invoke()

        assertTrue(fakeClientManager.destroyAllCalled)
        assertTrue(fakeAsyncBridge.closeCalled)
    }

    // 5. getManager — not yet implemented, TODO state
    @Test(expected = NotImplementedError::class)
    fun `getManager throws NotImplementedError`() {
        factory.getManager()
    }
}

private class FakeClientManager : ClientManager {

    var lastGetOrCreateTarget: Target? = null
    var destroyAllCalled = false
    val lastClient = object : SplitClient {
        override fun getTreatment(flag: String, evaluationOptions: io.split.client.thin.EvaluationOptions?) =
            throw UnsupportedOperationException()
        override fun getTreatments(flags: List<String>, evaluationOptions: io.split.client.thin.EvaluationOptions?) =
            throw UnsupportedOperationException()
        override fun getTreatmentsByFlagSets(flagSets: List<String>, evaluationOptions: io.split.client.thin.EvaluationOptions?) =
            throw UnsupportedOperationException()
        override suspend fun setTarget(target: Target) = throw UnsupportedOperationException()
        @Deprecated("Use suspend setTarget()", level = DeprecationLevel.ERROR)
        override fun setTargetAsync(target: Target, callback: SplitVoidCallback) = throw UnsupportedOperationException()
        override fun addEventListener(listener: io.split.client.thin.SplitEventListener) = throw UnsupportedOperationException()
        override suspend fun track(trafficType: String, eventType: String, value: Double?, properties: Map<String, Any?>?) = Unit
        @Deprecated("Use suspend track()", level = DeprecationLevel.ERROR)
        override fun trackAsync(trafficType: String, eventType: String, value: Double?, properties: Map<String, Any?>?, callback: SplitVoidCallback) = throw UnsupportedOperationException()
        override suspend fun destroy() = Unit
        @Deprecated("Use suspend destroy()", level = DeprecationLevel.ERROR)
        override fun destroyAsync(callback: SplitVoidCallback) = throw UnsupportedOperationException()
        override suspend fun flush() = Unit
        @Deprecated("Use suspend flush()", level = DeprecationLevel.ERROR)
        override fun flushAsync(callback: SplitVoidCallback) = throw UnsupportedOperationException()
    }

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
