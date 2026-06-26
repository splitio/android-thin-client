package io.split.client.thin.internal.observer

class DefaultCompositeObserver : CompositeObserver {

    private val observers = mutableListOf<Observer>()

    override fun register(observer: Observer) {
        synchronized(observers) {
            observers.add(observer)
        }
    }

    override fun unregister(observer: Observer) {
        synchronized(observers) {
            observers.remove(observer)
        }
    }

    override fun unregisterAll() {
        synchronized(observers) {
            observers.clear()
        }
    }

    override fun notifyEvent(event: ObservableEvent) {
        val snapshot = synchronized(observers) { observers.toList() }
        for (observer in snapshot) {
            try {
                observer.notifyEvent(event)
            } catch (_: Exception) {
                // fault isolation: one observer failing must not affect others
            }
        }
    }
}
