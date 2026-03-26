package io.split.client.thin.internal.lifecycle

import androidx.lifecycle.LifecycleOwner
import io.split.client.thin.internal.observer.CompositeObserver
import io.split.client.thin.internal.observer.ObservableEvent
import io.split.client.thin.internal.observer.ObservableEventType
import io.split.client.thin.internal.observer.Observer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class DefaultLifecycleManagerTest {

    private val fakeOwner: LifecycleOwner = mock(LifecycleOwner::class.java)

    @Test
    fun `pauseDispatchesToAllRegisteredComponents`() {
        val observer = FakeCompositeObserver()
        val manager = DefaultLifecycleManager(observer)
        val comp1 = FakeLifecycleComponent()
        val comp2 = FakeLifecycleComponent()
        manager.register(comp1)
        manager.register(comp2)

        manager.onStop(fakeOwner)

        assertEquals(1, comp1.pauseCount)
        assertEquals(1, comp2.pauseCount)
    }

    @Test
    fun `resumeDispatchesToAllRegisteredComponents`() {
        val observer = FakeCompositeObserver()
        val manager = DefaultLifecycleManager(observer)
        val comp1 = FakeLifecycleComponent()
        val comp2 = FakeLifecycleComponent()
        manager.register(comp1)
        manager.register(comp2)

        manager.onStart(fakeOwner)

        assertEquals(1, comp1.resumeCount)
        assertEquals(1, comp2.resumeCount)
    }

    @Test
    fun `destroyedManagerDoesNotDispatch`() {
        val observer = FakeCompositeObserver()
        val manager = DefaultLifecycleManager(observer)
        val comp = FakeLifecycleComponent()
        manager.register(comp)

        manager.destroy()
        manager.onStop(fakeOwner)
        manager.onStart(fakeOwner)

        assertEquals(0, comp.pauseCount)
        assertEquals(0, comp.resumeCount)
        assertTrue(observer.events.isEmpty())
    }

    @Test
    fun `notifiesObserverOnPause`() {
        val observer = FakeCompositeObserver()
        val manager = DefaultLifecycleManager(observer)

        manager.onStop(fakeOwner)

        assertEquals(listOf(ObservableEventType.SYNC_PAUSED), observer.events.map { it.type })
    }

    @Test
    fun `notifiesObserverOnResume`() {
        val observer = FakeCompositeObserver()
        val manager = DefaultLifecycleManager(observer)

        manager.onStart(fakeOwner)

        assertEquals(listOf(ObservableEventType.SYNC_RESUMED), observer.events.map { it.type })
    }

    @Test
    fun `doublePauseIsIdempotentForComponents`() {
        val observer = FakeCompositeObserver()
        val manager = DefaultLifecycleManager(observer)
        val comp = FakeLifecycleComponent()
        manager.register(comp)

        manager.onStop(fakeOwner)
        manager.onStop(fakeOwner)

        assertEquals(2, comp.pauseCount)
    }

    @Test
    fun `doubleResumeIsIdempotentForComponents`() {
        val observer = FakeCompositeObserver()
        val manager = DefaultLifecycleManager(observer)
        val comp = FakeLifecycleComponent()
        manager.register(comp)

        manager.onStart(fakeOwner)
        manager.onStart(fakeOwner)

        assertEquals(2, comp.resumeCount)
    }

    @Test
    fun `pauseContinuesWithRemainingComponentsWhenOneThrows`() {
        val observer = FakeCompositeObserver()
        val manager = DefaultLifecycleManager(observer)
        val throwing = ThrowingLifecycleComponent()
        val normal = FakeLifecycleComponent()
        manager.register(throwing)
        manager.register(normal)

        manager.onStop(fakeOwner)

        assertEquals(1, normal.pauseCount)
    }

    @Test
    fun `resumeContinuesWithRemainingComponentsWhenOneThrows`() {
        val observer = FakeCompositeObserver()
        val manager = DefaultLifecycleManager(observer)
        val throwing = ThrowingLifecycleComponent()
        val normal = FakeLifecycleComponent()
        manager.register(throwing)
        manager.register(normal)

        manager.onStart(fakeOwner)

        assertEquals(1, normal.resumeCount)
    }
}

private class ThrowingLifecycleComponent : LifecycleComponent {
    override fun pause() = throw RuntimeException("pause failed")
    override fun resume() = throw RuntimeException("resume failed")
}

private class FakeLifecycleComponent : LifecycleComponent {
    var pauseCount = 0
    var resumeCount = 0

    override fun pause() {
        pauseCount++
    }

    override fun resume() {
        resumeCount++
    }
}

private class FakeCompositeObserver : CompositeObserver {
    val events = mutableListOf<ObservableEvent>()

    override fun register(observer: Observer) {}
    override fun unregisterAll() {}
    override fun notifyEvent(event: ObservableEvent) {
        events.add(event)
    }
}
