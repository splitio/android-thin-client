package io.split.client.thin

/**
 * Read-only API for metadata available to the [SplitFactory].
 */
interface SplitManager {

    /**
     * Returns the names of flags currently available to the [SplitFactory] instance.
     */
    val flagNames: List<String>
}
