package io.split.client.thin.internal.persistence.domain

import io.split.client.thin.internal.persistence.GeneralPropertiesDao
import io.split.client.thin.internal.persistence.GeneralPropertiesEntity

internal class ConfigChangeDetector(
    private val generalPropertiesDao: GeneralPropertiesDao,
) {
    /**
     * Checks whether the config fingerprint (dynamicConfig + flagSets) differs from the stored value.
     * Updates the stored fingerprint if it changed.
     * Returns true if the config changed (caller should clear evaluations).
     *
     * On first run (no stored value), returns false — just persists the current fingerprint.
     */
    fun detectAndUpdate(dynamicConfig: Boolean, flagSets: Set<String>?): Boolean {
        val key = "configFingerprint"
        val stored = generalPropertiesDao.getByKey(key)?.value
        val current = "dc=$dynamicConfig|sets=${flagSets?.sorted()?.joinToString(",") ?: ""}"
        val changed = stored != null && stored != current
        if (stored != current) {
            generalPropertiesDao.insert(GeneralPropertiesEntity(key = key, value = current))
        }
        return changed
    }
}
