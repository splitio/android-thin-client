package io.split.client.thin.internal.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test

class NoOpSecureStorageTest {

    private val storage = NoOpSecureStorage<String>()
    private val target = "user-1"
    private val credential = JwtCredential(
        token = "test-token",
        expiresAt = System.currentTimeMillis() / 1000 + 3600,
        pushEnabled = false,
    )

    @Test
    fun `getCredential always returns null`() = runTest {
        assertNull(storage.getCredential(target))
    }

    @Test
    fun `getCredential returns null even after saveCredential`() = runTest {
        storage.saveCredential(credential, target)
        assertNull(storage.getCredential(target))
    }

    @Test
    fun `removeCredential is a no-op and does not throw`() = runTest {
        storage.removeCredential(target)
    }

    @Test
    fun `saveCredential is a no-op and does not throw`() = runTest {
        storage.saveCredential(credential, target)
    }
}
