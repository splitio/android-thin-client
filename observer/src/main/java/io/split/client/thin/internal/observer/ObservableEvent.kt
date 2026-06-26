package io.split.client.thin.internal.observer

data class ObservableEvent(
    val type: String,
    val properties: Map<String, String> = emptyMap(),
    val payload: Any? = null,
    val timestamp: Long = System.currentTimeMillis()
)
