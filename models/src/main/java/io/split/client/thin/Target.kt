package io.split.client.thin

class Target @JvmOverloads constructor(
    /** Key representing a single traffic type. */
    val key: Key,
    /** Optional target attributes sent for remote evaluation. */
    attributes: Map<String, Any?> = emptyMap(),
    /** Traffic type to be used when tracking events. */
    val trafficType: String,
) {
    val attributes: Map<String, Any?> = attributes.filterValues { isValidAttributeValue(it) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Target) return false
        return key == other.key && attributes == other.attributes && trafficType == other.trafficType
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + attributes.hashCode()
        result = 31 * result + trafficType.hashCode()
        return result
    }

    override fun toString(): String = "Target(key=$key, attributes=$attributes, trafficType=$trafficType)"

    fun copy(
        key: Key = this.key,
        attributes: Map<String, Any?> = this.attributes,
        trafficType: String = this.trafficType,
    ) = Target(key, attributes, trafficType)

    companion object {
        private fun isValidAttributeValue(value: Any?, depth: Int = 0): Boolean {
            if (depth > 10) return false
            return when (value) {
                null -> true
                is String -> true
                is Number -> true
                is Boolean -> true
                is Collection<*> -> value.all { it == null || isValidAttributeValue(it, depth + 1) }
                else -> false
            }
        }
    }
}
