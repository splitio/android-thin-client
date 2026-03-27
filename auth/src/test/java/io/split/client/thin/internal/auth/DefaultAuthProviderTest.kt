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
    private val fetcher = mock(CredentialFetcher::class.java) as CredentialFetcher<TestTarget>
    @Suppress("UNCHECKED_CAST")
    private val storage = mock(CredentialStorage::class.java) as CredentialStorage<TestTarget>

    private val target = TestTarget("user-1")
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

    private val target2 = TestTarget("user-2")
    private val compositeTarget = TestTarget("user-1,user-2")
    private val compositeKeyBuilder: (Set<TestTarget>) -> TestTarget = { targets ->
        TestTarget(targets.joinToString(",") { it.getUsers() })
    }

    private lateinit var authProvider: DefaultAuthProvider<TestTarget>

    @Before
    fun setUp() {
        authProvider = DefaultAuthProvider(fetcher, storage, compositeKeyBuilder)
    }

    @Test
    fun `credential returns cached composite token when valid`() = runTest {
        `when`(storage.getCredential(compositeTarget)).thenReturn(validCredential)

        val result = authProvider.credential(setOf(target, target2))

        assertSame(validCredential, result)
        verify(fetcher, never()).fetchCredential(compositeTarget)
    }

    @Test
    fun `credential fetches and saves under each individual target when cache miss`() = runTest {
        `when`(storage.getCredential(compositeTarget)).thenReturn(null)
        `when`(fetcher.fetchCredential(compositeTarget)).thenReturn(validCredential)

        val result = authProvider.credential(setOf(target, target2))

        assertEquals(validCredential, result)
        verify(fetcher).fetchCredential(compositeTarget)
        verify(storage).saveCredential(validCredential, compositeTarget)
        verify(storage).saveCredential(validCredential, target)
        verify(storage).saveCredential(validCredential, target2)
    }

    @Test
    fun `credential fetches when stored composite credential is expired`() = runTest {
        `when`(storage.getCredential(compositeTarget)).thenReturn(expiredCredential)
        `when`(fetcher.fetchCredential(compositeTarget)).thenReturn(validCredential)

        val result = authProvider.credential(setOf(target, target2))

        assertEquals(validCredential, result)
        verify(fetcher).fetchCredential(compositeTarget)
    }

    @Test
    fun `credential deduplicates concurrent fetch requests for same composite target`() = runTest {
        `when`(storage.getCredential(compositeTarget)).thenReturn(null)
        `when`(fetcher.fetchCredential(compositeTarget)).thenReturn(validCredential)

        val results = (1..5).map {
            async { authProvider.credential(setOf(target, target2)) }
        }.awaitAll()

        results.forEach { assertEquals(validCredential, it) }
        verify(fetcher, times(1)).fetchCredential(compositeTarget)
    }

    @Test
    fun `credential starts a new fetch after prior in-flight fetch is cancelled`() = runTest {
        var attempts = 0
        val firstFetchStarted = CompletableDeferred<Unit>()
        val cancelThenSucceedFetcher = CredentialFetcher<TestTarget> {
            attempts++
            if (attempts == 1) {
                firstFetchStarted.complete(Unit)
                awaitCancellation()
            } else {
                validCredential
            }
        }
        authProvider = DefaultAuthProvider<TestTarget>(cancelThenSucceedFetcher, storage, compositeKeyBuilder)
        `when`(storage.getCredential(compositeTarget)).thenReturn(null)

        val first = launch(Job()) { authProvider.credential(setOf(target, target2)) }
        firstFetchStarted.await()
        first.cancelAndJoin()

        val second = authProvider.credential(setOf(target, target2))

        assertEquals(validCredential, second)
        assertEquals(2, attempts)
    }

    @Test
    fun `credential fetches again after invalidateAll clears the composite`() = runTest {
        `when`(storage.getCredential(compositeTarget)).thenReturn(null)
        `when`(fetcher.fetchCredential(compositeTarget)).thenReturn(validCredential)

        authProvider.credential(setOf(target, target2))
        authProvider.invalidateAll(setOf(target, target2))

        `when`(storage.getCredential(compositeTarget)).thenReturn(null)
        authProvider.credential(setOf(target, target2))

        verify(fetcher, times(2)).fetchCredential(compositeTarget)
    }

    @Test
    fun `invalidateAll removes all specified targets from storage`() = runTest {
        authProvider.invalidateAll(setOf(target, target2))

        verify(storage).removeCredential(target)
        verify(storage).removeCredential(target2)
    }
}
