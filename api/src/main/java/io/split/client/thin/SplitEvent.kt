package io.split.client.thin

/**
 * SDK lifecycle/update events exposed to consumers.
 */
enum class SplitEvent {
    /** SDK completed initial remote sync and can evaluate from memory. */
    SDK_READY,
    /** SDK loaded usable evaluations from local cache. */
    SDK_READY_FROM_CACHE,
    /** SDK did not reach ready state before configured timeout. */
    SDK_READY_TIMEOUT,
    /** SDK received updated evaluations after ready. */
    SDK_UPDATE,
}
