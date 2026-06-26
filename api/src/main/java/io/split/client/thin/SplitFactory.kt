package io.split.client.thin

/**
 * Entry point for retrieving thin SDK clients and manager views.
 */
interface SplitFactory {

    /**
     * Returns a client associated with the provided target, or the default target if null.
     */
    fun getClient(target: Target? = null): SplitClient

    /**
     * Returns the factory manager view.
     */
    fun getManager(): SplitManager

    /**
     * Destroys all managed clients, flushing pending data and stopping all sync activity.
     */
    @JvmSynthetic
    suspend fun destroy()

    @Deprecated("Use suspend destroy()", level = DeprecationLevel.ERROR)
    fun destroyAsync(callback: SplitVoidCallback)
}
