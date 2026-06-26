package io.split.client.thin.internal.persistence.domain

import io.split.client.thin.internal.persistence.GeneralPropertiesDao
import io.split.client.thin.internal.persistence.GeneralPropertiesEntity
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.ArgumentMatchers.any
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigChangeDetectorTest {

    private val dao: GeneralPropertiesDao = mock(GeneralPropertiesDao::class.java)

    @Test
    fun `first run returns false and stores fingerprint`() {
        `when`(dao.getByKey("configFingerprint")).thenReturn(null)

        val result = ConfigChangeDetector(dao).detectAndUpdate(dynamicConfig = true, flagSets = null)

        assertFalse(result)
        verify(dao).insert(GeneralPropertiesEntity(key = "configFingerprint", value = "dc=true|sets="))
    }

    @Test
    fun `same fingerprint stored returns false and does not write`() {
        `when`(dao.getByKey("configFingerprint")).thenReturn(
            GeneralPropertiesEntity(key = "configFingerprint", value = "dc=false|sets=")
        )

        val result = ConfigChangeDetector(dao).detectAndUpdate(dynamicConfig = false, flagSets = null)

        assertFalse(result)
        verify(dao, never()).insert(any())
    }

    @Test
    fun `dynamicConfig change returns true and updates stored fingerprint`() {
        `when`(dao.getByKey("configFingerprint")).thenReturn(
            GeneralPropertiesEntity(key = "configFingerprint", value = "dc=false|sets=")
        )

        val result = ConfigChangeDetector(dao).detectAndUpdate(dynamicConfig = true, flagSets = null)

        assertTrue(result)
        verify(dao).insert(GeneralPropertiesEntity(key = "configFingerprint", value = "dc=true|sets="))
    }

    @Test
    fun `flagSets change returns true and updates stored fingerprint`() {
        `when`(dao.getByKey("configFingerprint")).thenReturn(
            GeneralPropertiesEntity(key = "configFingerprint", value = "dc=false|sets=set_a")
        )

        val result = ConfigChangeDetector(dao).detectAndUpdate(dynamicConfig = false, flagSets = setOf("set_b"))

        assertTrue(result)
        verify(dao).insert(GeneralPropertiesEntity(key = "configFingerprint", value = "dc=false|sets=set_b"))
    }

    @Test
    fun `flagSets are sorted in fingerprint`() {
        `when`(dao.getByKey("configFingerprint")).thenReturn(null)

        ConfigChangeDetector(dao).detectAndUpdate(dynamicConfig = false, flagSets = setOf("zz", "aa"))

        verify(dao).insert(GeneralPropertiesEntity(key = "configFingerprint", value = "dc=false|sets=aa,zz"))
    }

    @Test
    fun `migration from old dynamicConfig key - no stored configFingerprint returns false`() {
        `when`(dao.getByKey("configFingerprint")).thenReturn(null)

        val result = ConfigChangeDetector(dao).detectAndUpdate(dynamicConfig = false, flagSets = null)

        assertFalse(result)
    }
}
