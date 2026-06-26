package io.split.client.thin

/**
 * Generic callback for async compatibility methods.
 */
fun interface SplitCallback<T> {
    /**
     * Called when the operation completes, with either result or error.
     */
    fun onComplete(result: T?, error: Throwable?)
}

/**
 * Callback for async compatibility methods that do not return a value.
 */
fun interface SplitVoidCallback {
    /**
     * Called when the operation completes.
     */
    fun onComplete(error: Throwable?)
}
