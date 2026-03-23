package io.split.client.thin.internal.observer

interface ObserverRegistry {
    fun register(observer: Observer)
    fun unregisterAll()
}
