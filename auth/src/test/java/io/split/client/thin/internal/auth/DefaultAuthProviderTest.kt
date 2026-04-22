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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class DefaultAuthProviderTest {

    private val fetcher = mock(CredentialFetcher::class.java)
    private val storage = mock(CredentialStorage::class.java)

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

    private val target2 = "user-2"
    private val compositeTarget = "user-1,user-2"
    private val compositeKeyBuilder: (Set<String>) -> String = { targets ->
        targets.sorted().joinToString(",")
    }

    private lateinit var authProvider: DefaultAuthProvider

    @Before
    fun setUp() {
        authProvider = DefaultAuthProvider(fetcher, storage, compositeKeyBuilder, defaultTarget = target)
    }

    @Test
    fun `credential returns cached token when valid`() = runTest {
        `when`(storage.getCredential()).thenReturn(validCredential)

        val result = authProvider.credential(setOf(target, target2))

        assertSame(validCredential, result)
        verify(fetcher, never()).fetchCredential(compositeTarget)
    }

    @Test
    fun `credential fetches and saves single JWT on cache miss`() = runTest {
        `when`(storage.getCredential()).thenReturn(null)
        `when`(fetcher.fetchCredential(compositeTarget)).thenReturn(validCredential)

        val result = authProvider.credential(setOf(target, target2))

        assertEquals(validCredential, result)
        verify(fetcher).fetchCredential(compositeTarget)
        verify(storage).saveCredential(validCredential)
    }

    @Test
    fun `credential fetches when stored credential is expired`() = runTest {
        `when`(storage.getCredential()).thenReturn(expiredCredential)
        `when`(fetcher.fetchCredential(compositeTarget)).thenReturn(validCredential)

        val result = authProvider.credential(setOf(target, target2))

        assertEquals(validCredential, result)
        verify(fetcher).fetchCredential(compositeTarget)
    }

    @Test
    fun `credential deduplicates concurrent fetch requests for same composite target`() = runTest {
        `when`(storage.getCredential()).thenReturn(null)
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
        val cancelThenSucceedFetcher = CredentialFetcher {
            attempts++
            if (attempts == 1) {
                firstFetchStarted.complete(Unit)
                awaitCancellation()
            } else {
                validCredential
            }
        }
        authProvider = DefaultAuthProvider(cancelThenSucceedFetcher, storage, compositeKeyBuilder, defaultTarget = target)
        `when`(storage.getCredential()).thenReturn(null)

        val first = launch(Job()) { authProvider.credential(setOf(target, target2)) }
        firstFetchStarted.await()
        first.cancelAndJoin()

        val second = authProvider.credential(setOf(target, target2))

        assertEquals(validCredential, second)
        assertEquals(2, attempts)
    }

    @Test
    fun `credential fetches again after invalidateAll clears storage`() = runTest {
        `when`(storage.getCredential()).thenReturn(null)
        `when`(fetcher.fetchCredential(compositeTarget)).thenReturn(validCredential)

        authProvider.credential(setOf(target, target2))
        authProvider.invalidateAll()

        `when`(storage.getCredential()).thenReturn(null)
        authProvider.credential(setOf(target, target2))

        verify(fetcher, times(2)).fetchCredential(compositeTarget)
    }

    @Test
    fun `invalidateAll removes credential from storage`() = runTest {
        authProvider.invalidateAll()

        verify(storage).removeCredential()
    }

    @Test
    fun `addTarget with defaultTarget returns false - target already known`() {
        val result = authProvider.addTarget(target)

        assertFalse(result)
    }

    @Test
    fun `addTarget with non-default target returns true - genuinely new target`() {
        val result = authProvider.addTarget("user-2")

        assertTrue(result)
    }

    @Test
    fun `credential no-arg uses defaultTarget seeded in activeTargets`() = runTest {
        `when`(storage.getCredential()).thenReturn(null)
        `when`(fetcher.fetchCredential(target)).thenReturn(validCredential)

        authProvider.credential()

        verify(fetcher).fetchCredential(target)
    }

    @Test
    fun `addTarget with defaultTarget during in-flight fetch does not cause duplicate fetch`() = runTest {
        val fetchStarted = CompletableDeferred<Unit>()
        val fetchUnblocked = CompletableDeferred<Unit>()
        var fetchCount = 0
        val blockingFetcher = CredentialFetcher {
            fetchCount++
            fetchStarted.complete(Unit)
            fetchUnblocked.await()
            validCredential
        }
        authProvider = DefaultAuthProvider(blockingFetcher, storage, compositeKeyBuilder, defaultTarget = target)
        `when`(storage.getCredential()).thenReturn(null)

        val coroutineA = launch { authProvider.credential() }
        fetchStarted.await()

        // Mirrors DefaultClientManager.getOrCreate: if addTarget returns true, call invalidateAll
        val isNew = authProvider.addTarget(target)
        if (isNew) authProvider.invalidateAll()

        val coroutineB = async { authProvider.credential() }
        fetchUnblocked.complete(Unit)
        coroutineA.join()
        coroutineB.await()

        assertEquals(1, fetchCount)
    }

    @Test
    fun `composite key is sorted regardless of set iteration order`() = runTest {
        `when`(storage.getCredential()).thenReturn(null)
        `when`(fetcher.fetchCredential("user-1,user-2")).thenReturn(validCredential)

        // Pass targets in reverse order
        authProvider.credential(setOf(target2, target))

        verify(fetcher).fetchCredential("user-1,user-2")
    }

    // ---- Ref-counting tests (fix #4) ----

    @Test
    fun `removeTarget does not remove key when a second client shares the same matchingKey`() {
        // Two clients with the same matchingKey — removing one should leave it in the active set
        authProvider = DefaultAuthProvider(fetcher, storage, compositeKeyBuilder)
        authProvider.addTarget("shared-key") // ref=1
        authProvider.addTarget("shared-key") // ref=2

        val allEmpty = authProvider.removeTarget("shared-key") // ref=1 — NOT empty

        assertFalse("Target set should not be empty while a second client still holds the key", allEmpty)
    }

    @Test
    fun `removeTarget returns true only when last ref for that key is removed`() {
        authProvider = DefaultAuthProvider(fetcher, storage, compositeKeyBuilder)
        authProvider.addTarget("shared-key") // ref=1
        authProvider.addTarget("shared-key") // ref=2

        authProvider.removeTarget("shared-key") // ref=1
        val allEmpty = authProvider.removeTarget("shared-key") // ref=0 — empty

        assertTrue("Target set should be empty after removing all refs", allEmpty)
    }

    @Test
    fun `addTarget returns true only on first add of a new key`() {
        authProvider = DefaultAuthProvider(fetcher, storage, compositeKeyBuilder)

        val first = authProvider.addTarget("key-a")
        val second = authProvider.addTarget("key-a")

        assertTrue("First add should return true (new target)", first)
        assertFalse("Second add should return false (existing target)", second)
    }

    @Test
    fun `credential no-arg includes all uniquely-keyed active targets`() = runTest {
        authProvider = DefaultAuthProvider(fetcher, storage, compositeKeyBuilder)
        authProvider.addTarget("user-a")
        authProvider.addTarget("user-b")
        `when`(storage.getCredential()).thenReturn(null)
        `when`(fetcher.fetchCredential("user-a,user-b")).thenReturn(validCredential)

        authProvider.credential()

        verify(fetcher).fetchCredential("user-a,user-b")
    }

    // ---- invalidateAll cancels in-flight Deferreds (fix #9) ----

    @Test
    fun `invalidateAll cancels in-flight fetch and allows fresh fetch after`() = runTest {
        val fetchStarted = CompletableDeferred<Unit>()
        var fetchAttempts = 0
        val blockingFetcher = CredentialFetcher {
            fetchAttempts++
            if (fetchAttempts == 1) {
                fetchStarted.complete(Unit)
                awaitCancellation()
            } else {
                validCredential
            }
        }
        authProvider = DefaultAuthProvider(blockingFetcher, storage, compositeKeyBuilder, defaultTarget = target)
        `when`(storage.getCredential()).thenReturn(null)

        // Start a fetch that will block
        val inflightJob = launch(Job()) { authProvider.credential() }
        fetchStarted.await()

        // invalidateAll should cancel the in-flight deferred
        authProvider.invalidateAll()
        inflightJob.cancelAndJoin()

        // A subsequent fetch should issue a new network call
        `when`(storage.getCredential()).thenReturn(null)
        val result = authProvider.credential()

        assertEquals(validCredential, result)
        assertEquals(2, fetchAttempts)
    }
}
