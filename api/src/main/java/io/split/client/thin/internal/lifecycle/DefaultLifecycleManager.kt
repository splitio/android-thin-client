package io.split.client.thin.internal.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.split.client.thin.internal.observer.CompositeObserver
import io.split.client.thin.internal.observer.ObservableEvent
import io.split.client.thin.internal.observer.ObservableEventType
internal class DefaultLifecycleManager(
    private val compositeObserver: CompositeObserver,
    observerRegistrar: (DefaultLifecycleObserver) -> Unit = {},
    private val observerUnregistrar: (DefaultLifecycleObserver) -> Unit = {},
) : LifecycleManager, DefaultLifecycleObserver {

    private val components = mutableListOf<LifecycleComponent>()
    private var destroyed = false

    init {
        observerRegistrar(this)
    }

    override fun register(component: LifecycleComponent) {
        components.add(component)
    }

    override fun onStop(owner: LifecycleOwner) {
        if (destroyed) return
        components.toList().forEach { runCatching { it.pause() } }
        compositeObserver.notifyEvent(ObservableEvent(ObservableEventType.SYNC_PAUSED))
    }

    override fun onStart(owner: LifecycleOwner) {
        if (destroyed) return
        components.toList().forEach { runCatching { it.resume() } }
        compositeObserver.notifyEvent(ObservableEvent(ObservableEventType.SYNC_RESUMED))
    }

    override fun destroy() {
        destroyed = true
        observerUnregistrar(this)
    }
}
