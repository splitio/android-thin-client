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

    @Suppress("UNCHECKED_CAST")
    private val secureStorage = mock(SecureStorage::class.java) as SecureStorage<String>
    private lateinit var storage: InMemoryCredentialStorage<String>

    private val target = "user-1"
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
        `when`(secureStorage.getCredential(target)).thenReturn(null)

        val result = storage.getCredential(target)

        assertNull(result)
    }

    @Test
    fun `getCredential returns credential from SecureStorage on cache miss`() = runTest {
        `when`(secureStorage.getCredential(target)).thenReturn(credential)

        val result = storage.getCredential(target)

        assertEquals(credential, result)
        verify(secureStorage).getCredential(target)
    }

    @Test
    fun `getCredential returns cached credential without hitting SecureStorage on second call`() = runTest {
        `when`(secureStorage.getCredential(target)).thenReturn(credential)
        storage.getCredential(target) // populates cache

        val result = storage.getCredential(target)

        assertEquals(credential, result)
        verify(secureStorage).getCredential(target) // called only once
    }

    @Test
    fun `saveCredential stores in memory and delegates to SecureStorage`() = runTest {
        storage.saveCredential(credential, target)

        val result = storage.getCredential(target)

        assertEquals(credential, result)
        verify(secureStorage).saveCredential(credential, target)
        verify(secureStorage, never()).getCredential(target)
    }

    @Test
    fun `removeCredential clears in-memory cache and delegates to SecureStorage`() = runTest {
        storage.saveCredential(credential, target)
        storage.removeCredential(target)

        `when`(secureStorage.getCredential(target)).thenReturn(null)
        val result = storage.getCredential(target)

        assertNull(result)
        verify(secureStorage).removeCredential(target)
    }

    @Test
    fun `different targets are stored independently`() = runTest {
        val target2 = "user-2"
        val credential2 = JwtCredential(
            token = "token-2",
            expiresAt = System.currentTimeMillis() / 1000 + 7200,
            pushEnabled = true,
        )
        `when`(secureStorage.getCredential(target)).thenReturn(null)
        `when`(secureStorage.getCredential(target2)).thenReturn(null)

        storage.saveCredential(credential, target)
        storage.saveCredential(credential2, target2)

        assertEquals(credential, storage.getCredential(target))
        assertEquals(credential2, storage.getCredential(target2))
    }
}
