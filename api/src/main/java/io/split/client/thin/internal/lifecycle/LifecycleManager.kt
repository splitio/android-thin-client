package io.split.client.thin.internal.lifecycle

internal interface LifecycleManager {
    fun register(component: LifecycleComponent)
    fun destroy()
}
