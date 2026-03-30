package io.split.client.thin.internal.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class InMemoryCredentialStorageTest {

    private val secureStorage = mock(SecureStorage::class.java)
    private lateinit var storage: InMemoryCredentialStorage

    private val credential = JwtCredential(
        token = "test-token",
        expiresAt = System.currentTimeMillis() / 1000 + 3600,
        pushEnabled = false,
    )

    @Before
    fun setUp() {
        storage = InMemoryCredentialStorage(secureStorage)
    }

    @Test
    fun `getCredential returns null on cold cache miss with no SecureStorage fallback`() = runTest {
        `when`(secureStorage.getCredential()).thenReturn(null)

        val result = storage.getCredential()

        assertNull(result)
    }

    @Test
    fun `getCredential returns credential from SecureStorage on cache miss`() = runTest {
        `when`(secureStorage.getCredential()).thenReturn(credential)

        val result = storage.getCredential()

        assertEquals(credential, result)
        verify(secureStorage).getCredential()
    }

    @Test
    fun `getCredential returns cached credential without hitting SecureStorage on second call`() = runTest {
        `when`(secureStorage.getCredential()).thenReturn(credential)
        storage.getCredential() // populates cache

        val result = storage.getCredential()

        assertEquals(credential, result)
        verify(secureStorage).getCredential() // called only once
    }

    @Test
    fun `saveCredential stores in memory and delegates to SecureStorage`() = runTest {
        storage.saveCredential(credential)

        val result = storage.getCredential()

        assertEquals(credential, result)
        verify(secureStorage).saveCredential(credential)
        verify(secureStorage, never()).getCredential()
    }

    @Test
    fun `removeCredential clears in-memory cache and delegates to SecureStorage`() = runTest {
        storage.saveCredential(credential)
        storage.removeCredential()

        `when`(secureStorage.getCredential()).thenReturn(null)
        val result = storage.getCredential()

        assertNull(result)
        verify(secureStorage).removeCredential()
    }
}
