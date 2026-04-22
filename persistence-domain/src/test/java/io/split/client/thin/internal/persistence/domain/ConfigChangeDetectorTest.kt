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
    fun `first run returns false and stores value`() {
        `when`(dao.getByKey("dynamicConfig")).thenReturn(null)

        val result = ConfigChangeDetector(dao).detectAndUpdate(true)

        assertFalse(result)
        verify(dao).insert(GeneralPropertiesEntity(key = "dynamicConfig", value = "true"))
    }

    @Test
    fun `same value stored returns false and does not write`() {
        `when`(dao.getByKey("dynamicConfig")).thenReturn(
            GeneralPropertiesEntity(key = "dynamicConfig", value = "false")
        )

        val result = ConfigChangeDetector(dao).detectAndUpdate(false)

        assertFalse(result)
        verify(dao, never()).insert(any())
    }

    @Test
    fun `different value stored returns true and updates stored value`() {
        `when`(dao.getByKey("dynamicConfig")).thenReturn(
            GeneralPropertiesEntity(key = "dynamicConfig", value = "false")
        )

        val result = ConfigChangeDetector(dao).detectAndUpdate(true)

        assertTrue(result)
        verify(dao).insert(GeneralPropertiesEntity(key = "dynamicConfig", value = "true"))
    }
}
