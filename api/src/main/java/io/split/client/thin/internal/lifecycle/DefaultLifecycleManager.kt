package io.split.client.thin.internal.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.split.client.thin.internal.observer.CompositeObserver
import io.split.client.thin.internal.observer.ObservableEvent
import io.split.client.thin.internal.observer.ObservableEventType
import java.lang.ref.WeakReference

internal class DefaultLifecycleManager(
    private val compositeObserver: CompositeObserver,
    observerRegistrar: (DefaultLifecycleObserver) -> Unit = {},
    private val observerUnregistrar: (DefaultLifecycleObserver) -> Unit = {},
) : LifecycleManager, DefaultLifecycleObserver {

    private val components = mutableListOf<WeakReference<LifecycleComponent>>()
    private var destroyed = false

    init {
        observerRegistrar(this)
    }

    override fun register(component: LifecycleComponent) {
        components.add(WeakReference(component))
    }

    override fun onStop(owner: LifecycleOwner) {
        if (destroyed) return
        components.forEach { runCatching { it.get()?.pause() } }
        compositeObserver.notifyEvent(ObservableEvent(ObservableEventType.SYNC_PAUSED))
    }

    override fun onStart(owner: LifecycleOwner) {
        if (destroyed) return
        components.forEach { runCatching { it.get()?.resume() } }
        compositeObserver.notifyEvent(ObservableEvent(ObservableEventType.SYNC_RESUMED))
    }

    override fun destroy() {
        destroyed = true
        observerUnregistrar(this)
    }
}
