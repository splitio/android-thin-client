package io.split.client.thin.internal.persistence.domain

import io.split.client.thin.internal.persistence.GeneralPropertiesDao
import io.split.client.thin.internal.persistence.GeneralPropertiesEntity

internal class ConfigChangeDetector(
    private val generalPropertiesDao: GeneralPropertiesDao,
) {
    /**
     * Checks whether [dynamicConfig] differs from the stored value.
     * Updates the stored value if it changed.
     * Returns true if the config changed (caller should clear evaluations).
     *
     * On first run (no stored value), returns false — just persists the current value.
     */
    fun detectAndUpdate(dynamicConfig: Boolean): Boolean {
        val key = "dynamicConfig"
        val stored = generalPropertiesDao.getByKey(key)?.value
        val current = dynamicConfig.toString()
        val changed = stored != null && stored != current
        if (stored != current) {
            generalPropertiesDao.insert(GeneralPropertiesEntity(key = key, value = current))
        }
        return changed
    }
}
