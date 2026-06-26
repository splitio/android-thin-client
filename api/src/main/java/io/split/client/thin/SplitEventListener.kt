package io.split.client.thin


/**
 * Listener for SDK lifecycle and update events.
 *
 * Extend this class and override only the callbacks you need:
 * - Background callbacks (for example, [onUpdate]) run immediately on a background thread.
 * - UI callbacks (for example, [onUpdateView]) run on the main thread.
 *
 * Example usage:
 * ```
 * client.addEventListener(object : SplitEventListener() {
 *     override fun onReady(client: SplitClient, metadata: SdkReadyMetadata) {
 *         val initialCacheLoad = metadata.isInitialCacheLoad
 *         // Handle ready on background thread
 *     }
 *
 *     override fun onUpdate(client: SplitClient, metadata: SdkUpdateMetadata) {
 *         val type = metadata.type // FLAGS_UPDATE or SEGMENTS_UPDATE
 *         val names = metadata.names // updated flag/segment names
 *         // Handle updates on background thread
 *     }
 *
 *     override fun onReadyFromCacheView(client: SplitClient, metadata: SdkReadyMetadata) {
 *         // Handle cache ready on main/UI thread
 *         val initialCacheLoad = metadata.isInitialCacheLoad
 *     }
 * })
 * ```
 */
open class SplitEventListener {

    open fun onReady(client: SplitClient, metadata: SdkReadyMetadata?) {
        // not implemented by default
    }

    open fun onReadyView(client: SplitClient, metadata: SdkReadyMetadata?) {
        // not implemented by default
    }

    open fun onUpdate(client: SplitClient, metadata: SdkUpdateMetadata?) {
        // not implemented by default
    }

    open fun onReadyFromCache(client: SplitClient, metadata: SdkReadyMetadata?) {
        // not implemented by default
    }

    open fun onUpdateView(client: SplitClient, metadata: SdkUpdateMetadata?) {
        // not implemented by default
    }

    open fun onReadyFromCacheView(client: SplitClient, metadata: SdkReadyMetadata?) {
        // not implemented by default
    }

    open fun onTimeout(client: SplitClient) {
        // not implemented by default
    }

    open fun onTimeoutView(client: SplitClient) {
        // not implemented by default
    }
}
