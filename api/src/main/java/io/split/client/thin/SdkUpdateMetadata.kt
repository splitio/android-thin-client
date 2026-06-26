package io.split.client.thin

/**
 * Typed metadata for SDK_UPDATE events.
 *
 * Contains information about the type of update and the names of entities that were updated.
 */
data class SdkUpdateMetadata(
    /**
     * Returns the type of update that triggered this event.
     *
     * @return the update type, or null if not available
     */
    val type: Type? = null,

    /**
     * Returns the list of entity names that changed in this update.
     *
     *
     * For [Type.FLAGS_UPDATE], this contains flag names that were updated.
     * For [Type.SEGMENTS_UPDATE], this is always an empty list (segment names are not included).
     *
     * @return the list of updated entity names, or null if not available
     */
    val names: List<String>? = emptyList()
) {
    /**
     * The type of update that triggered the SDK_UPDATE event.
     */
    enum class Type {
        /**
         * Feature flags were updated.
         *
         * [.names] returns the list of flag names that changed.
         */
        FLAGS_UPDATE,

        /**
         * Segments were updated (rule-based segments, memberships, or large segments).
         *
         * Note: [.names] always returns an empty list for this type.
         * Segment names are not included in the metadata.
         */
        SEGMENTS_UPDATE
    }
}
