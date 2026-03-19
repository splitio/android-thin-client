package io.split.client.thin.internal.observer

fun interface Observer {
    fun notifyEvent(event: ObservableEvent)
}
