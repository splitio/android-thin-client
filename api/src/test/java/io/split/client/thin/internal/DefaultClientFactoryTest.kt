package io.split.client.thin.internal

import io.split.client.thin.Key
import io.split.client.thin.Target
import io.split.client.thin.internal.observer.CompositeObserver
import io.split.client.thin.internal.observer.ObservableEvent
import io.split.client.thin.internal.observer.Observer
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultClientFactoryTest {

    @Test
    fun `invoke creates DefaultSplitClient`() {
        val factory = DefaultClientFactory(
            FakeCompositeObserver(),
            TestScope(),
            evaluationRepository = FakeEvaluationRepository(),
            filters = null,
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
            filters = null,
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
            filters = null,
            fallbackCalculator = null,
        )

        factory(target)
        advanceUntilIdle()

        assertEquals(1, repository.setTargetCalls.size)
        assertEquals(target, repository.setTargetCalls[0].first)
    }

}

private class FakeCompositeObserver : CompositeObserver {
    val registeredObservers = mutableListOf<Observer>()

    override fun register(observer: Observer) {
        registeredObservers.add(observer)
    }

    override fun unregisterAll() {
        registeredObservers.clear()
    }

    override fun notifyEvent(event: ObservableEvent) {
        registeredObservers.forEach { it.notifyEvent(event) }
    }
}

