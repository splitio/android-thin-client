package io.split.client.thin.internal

import io.split.client.thin.Key
import io.split.client.thin.Target
import io.split.client.thin.internal.evaluation.EvaluationFetchCoordinator
import io.split.client.thin.internal.evaluation.EvaluationPeriodicScheduler
import io.split.client.thin.internal.observer.CompositeObserver
import io.split.client.thin.internal.observer.ObservableEvent
import io.split.client.thin.internal.observer.Observer
import kotlinx.coroutines.test.TestScope
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
            fetchCoordinator = FakeEvaluationFetchCoordinator(),
            schedulerIntervalMillis = 3_600_000L,
        )

        val client = factory(Target(Key("user-1")))

        assertTrue(client is DefaultSplitClient)
    }

    @Test
    fun `invoke starts scheduler with the given target`() {
        val fakeScheduler = FakeEvaluationPeriodicScheduler()
        val target = Target(Key("user-1"))
        val factory = DefaultClientFactory(
            FakeCompositeObserver(),
            TestScope(),
            evaluationRepository = FakeEvaluationRepository(),
            filters = null,
            fallbackCalculator = null,
            fetchCoordinator = FakeEvaluationFetchCoordinator(),
            schedulerIntervalMillis = 3_600_000L,
            schedulerFactory = { _, _ -> fakeScheduler },
        )

        factory(target)

        assertEquals(1, fakeScheduler.startCalls.size)
        assertEquals(target, fakeScheduler.startCalls[0].first)
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
            fetchCoordinator = FakeEvaluationFetchCoordinator(),
            schedulerIntervalMillis = 3_600_000L,
        )

        factory(Target(Key("user-1")))

        assertEquals(1, compositeObserver.registeredObservers.size)
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
