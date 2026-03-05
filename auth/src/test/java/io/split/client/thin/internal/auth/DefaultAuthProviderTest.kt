package io.split.client.thin.internal.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class DefaultAuthProviderTest {

    @Suppress("UNCHECKED_CAST")
    private val fetcher = mock(CredentialFetcher::class.java) as CredentialFetcher<String>
    @Suppress("UNCHECKED_CAST")
    private val storage = mock(CredentialStorage::class.java) as CredentialStorage<String>

    private val target = "user-1"
    private val validCredential = JwtCredential(
        token = "valid-token",
        expiresAt = System.currentTimeMillis() / 1000 + 3600,
        pushEnabled = false,
    )
    private val expiredCredential = JwtCredential(
        token = "expired-token",
        expiresAt = System.currentTimeMillis() / 1000 - 60,
        pushEnabled = false,
    )

    private lateinit var authProvider: DefaultAuthProvider<String>

    @Before
    fun setUp() {
        authProvider = DefaultAuthProvider(fetcher, storage)
    }

    @Test
    fun `credential returns stored credential when valid and non-expired`() = runTest {
        `when`(storage.getCredential(target)).thenReturn(validCredential)

        val result = authProvider.credential(target)

        assertSame(validCredential, result)
        verify(fetcher, never()).fetchCredential(target)
    }

    @Test
    fun `credential fetches when storage returns null`() = runTest {
        `when`(storage.getCredential(target)).thenReturn(null)
        `when`(fetcher.fetchCredential(target)).thenReturn(validCredential)

        val result = authProvider.credential(target)

        assertEquals(validCredential, result)
        verify(fetcher).fetchCredential(target)
        verify(storage).saveCredential(validCredential, target)
    }

    @Test
    fun `credential fetches when stored credential is expired`() = runTest {
        `when`(storage.getCredential(target)).thenReturn(expiredCredential)
        `when`(fetcher.fetchCredential(target)).thenReturn(validCredential)

        val result = authProvider.credential(target)

        assertEquals(validCredential, result)
        verify(fetcher).fetchCredential(target)
        verify(storage).saveCredential(validCredential, target)
    }

    @Test
    fun `credential deduplicates concurrent fetch requests for same target`() = runTest {
        `when`(storage.getCredential(target)).thenReturn(null)
        `when`(fetcher.fetchCredential(target)).thenReturn(validCredential)

        val results = (1..5).map {
            async { authProvider.credential(target) }
        }.awaitAll()

        results.forEach { assertEquals(validCredential, it) }
        verify(fetcher, times(1)).fetchCredential(target)
    }

    @Test
    fun `credential starts a new fetch after prior in-flight fetch is cancelled`() = runTest {
        var attempts = 0
        // Ensure cancellation happens after the first fetch has actually started.
        val firstFetchStarted = CompletableDeferred<Unit>()
        val cancelThenSucceedFetcher = CredentialFetcher<String> {
            attempts++
            if (attempts == 1) {
                firstFetchStarted.complete(Unit)
                awaitCancellation()
            } else {
                validCredential
            }
        }
        authProvider = DefaultAuthProvider(cancelThenSucceedFetcher, storage)
        `when`(storage.getCredential(target)).thenReturn(null)

        // Use an independent Job so cancelling this fetch does not cancel the test scope.
        val first = launch(Job()) { authProvider.credential(target) }
        firstFetchStarted.await()
        first.cancelAndJoin()

        val second = authProvider.credential(target)

        assertEquals(validCredential, second)
        assertEquals(2, attempts)
    }

    @Test
    fun `invalidate removes credential from storage`() = runTest {
        authProvider.invalidate(target)

        verify(storage).removeCredential(target)
    }

    @Test
    fun `credential fetches again after invalidate`() = runTest {
        `when`(storage.getCredential(target)).thenReturn(null)
        `when`(fetcher.fetchCredential(target)).thenReturn(validCredential)

        authProvider.credential(target)
        authProvider.invalidate(target)

        `when`(storage.getCredential(target)).thenReturn(null)
        authProvider.credential(target)

        verify(fetcher, times(2)).fetchCredential(target)
    }
}
