package io.split.client.thin.internal.observer

import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DefaultCompositeObserverTest {

    private val composite = DefaultCompositeObserver()

    @Test
    fun `notifyEvent dispatches to registered observer`() {
        val observer = mock(Observer::class.java)
        val event = ObservableEvent("test_event")

        composite.register(observer)
        composite.notifyEvent(event)

        verify(observer).notifyEvent(event)
    }

    @Test
    fun `notifyEvent dispatches to multiple observers`() {
        val observer1 = mock(Observer::class.java)
        val observer2 = mock(Observer::class.java)
        val event = ObservableEvent("test_event")

        composite.register(observer1)
        composite.register(observer2)
        composite.notifyEvent(event)

        verify(observer1).notifyEvent(event)
        verify(observer2).notifyEvent(event)
    }

    @Test
    fun `one observer throwing does not prevent others from receiving event`() {
        val throwingObserver = Observer { throw RuntimeException("boom") }
        val goodObserver = mock(Observer::class.java)
        val event = ObservableEvent("test_event")

        composite.register(throwingObserver)
        composite.register(goodObserver)

        composite.notifyEvent(event)

        verify(goodObserver).notifyEvent(event)
    }

    @Test
    fun `unregisterAll clears all observers`() {
        val observer = mock(Observer::class.java)
        val event = ObservableEvent("test_event")

        composite.register(observer)
        composite.unregisterAll()
        composite.notifyEvent(event)

        verify(observer, never()).notifyEvent(event)
    }

    @Test
    fun `unregister removes observer so it no longer receives events`() {
        val observer = mock(Observer::class.java)
        val event = ObservableEvent("test_event")

        composite.register(observer)
        composite.unregister(observer)
        composite.notifyEvent(event)

        verify(observer, never()).notifyEvent(event)
    }

    @Test
    fun `unregister only removes the specified observer leaving others intact`() {
        val observer1 = mock(Observer::class.java)
        val observer2 = mock(Observer::class.java)
        val event = ObservableEvent("test_event")

        composite.register(observer1)
        composite.register(observer2)
        composite.unregister(observer1)
        composite.notifyEvent(event)

        verify(observer1, never()).notifyEvent(event)
        verify(observer2).notifyEvent(event)
    }

    @Test
    fun `concurrent registration and notification does not throw`() {
        val latch = CountDownLatch(10)
        val executor = Executors.newFixedThreadPool(10)

        repeat(10) { i ->
            executor.submit {
                try {
                    composite.register { _ -> /* no-op */ }
                    composite.notifyEvent(ObservableEvent("event_$i"))
                } finally {
                    latch.countDown()
                }
            }
        }

        if (!latch.await(5, TimeUnit.SECONDS)) {
            fail("Concurrent test timed out")
        }
        executor.shutdown()
    }
}
