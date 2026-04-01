package io.split.client.thin.internal.persistence.domain.evaluation

import io.split.client.thin.EvaluationResult
import io.split.client.thin.Key
import io.split.client.thin.internal.evaluation.EvaluationChange
import io.split.client.thin.internal.evaluation.EvaluationKey
import io.split.client.thin.internal.evaluation.StoredEvaluation
import io.split.client.thin.internal.persistence.PersistentEvaluationData
import io.split.client.thin.internal.persistence.PersistentEvaluationStorage
import io.split.client.thin.internal.persistence.SerializedEvaluation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultEvaluationPersistenceManagerTest {

    private lateinit var persistentStorage: PersistentEvaluationStorage
    private lateinit var callbacks: EvaluationPersistenceCallbacks
    private lateinit var targetHasher: TargetHasher
    private lateinit var evalSerializer: StoredEvaluationSerializer
    private lateinit var scope: TestScope
    private lateinit var manager: DefaultEvaluationPersistenceManager

    private val testKey = Key(matchingKey = "user1", bucketingKey = null)
    private val testEvalKey = EvaluationKey(key = testKey, attributes = mapOf("age" to 30L))
    private val testHashedTarget = HashedTarget("testKeyHash", "testAttrsHash")

    private fun makeStoredEvaluation(flag: String = "my_flag"): StoredEvaluation =
        StoredEvaluation(
            result = EvaluationResult(flag = flag, treatment = "on"),
            flagSets = setOf("set1")
        )

    @Before
    fun setUp() {
        persistentStorage = mock(PersistentEvaluationStorage::class.java)
        callbacks = mock(EvaluationPersistenceCallbacks::class.java)
        targetHasher = mock(TargetHasher::class.java)
        evalSerializer = mock(StoredEvaluationSerializer::class.java)
        scope = TestScope()
        manager = DefaultEvaluationPersistenceManager(
            persistentStorage, callbacks, targetHasher, evalSerializer, scope
        )

        `when`(targetHasher.hash(testEvalKey)).thenReturn(testHashedTarget)
    }

    // --- loadLocal tests ---

    @Test
    fun `loadLocal returns null when no persisted data`() = scope.runTest {
        `when`(persistentStorage.loadForKey("testKeyHash", "testAttrsHash")).thenReturn(null)

        val result = manager.loadLocal(testEvalKey)

        assertNull(result)
        verify(callbacks).onLoadStarted()
        verify(callbacks, never()).onLoadSucceeded(anyLong())
    }

    @Test
    fun `loadLocal returns EvaluationChange when data exists`() = scope.runTest {
        val evalJson = """{"result":{"flag":"my_flag","treatment":"on"},"flagSets":["set1"]}"""
        val storedEval = makeStoredEvaluation()
        val persistedData = PersistentEvaluationData(changeNumber = 42L, evaluations = listOf(evalJson))

        `when`(persistentStorage.loadForKey("testKeyHash", "testAttrsHash")).thenReturn(persistedData)
        `when`(evalSerializer.deserialize(evalJson)).thenReturn(storedEval)

        val result = manager.loadLocal(testEvalKey)

        val expected = EvaluationChange(evaluationKey = testEvalKey, changeNumber = 42L, evaluations = listOf(storedEval))
        assertEquals(expected, result)
    }

    @Test
    fun `loadLocal uses caller evalKey directly without reconstructing attributes`() = scope.runTest {
        val evalJson = """{"result":{"flag":"my_flag","treatment":"on"},"flagSets":["set1"]}"""
        val storedEval = makeStoredEvaluation()
        val persistedData = PersistentEvaluationData(changeNumber = 42L, evaluations = listOf(evalJson))

        `when`(persistentStorage.loadForKey("testKeyHash", "testAttrsHash")).thenReturn(persistedData)
        `when`(evalSerializer.deserialize(evalJson)).thenReturn(storedEval)

        val result = manager.loadLocal(testEvalKey)

        // The returned EvaluationKey should be exactly the caller's evalKey
        assertEquals(testEvalKey, result?.evaluationKey)
        assertEquals(testEvalKey.attributes, result?.evaluationKey?.attributes)
    }

    @Test
    fun `loadLocal calls onLoadStarted and onLoadSucceeded when data exists`() = scope.runTest {
        val evalJson = """{"result":{"flag":"my_flag","treatment":"on"},"flagSets":["set1"]}"""
        val storedEval = makeStoredEvaluation()
        val persistedData = PersistentEvaluationData(changeNumber = 42L, evaluations = listOf(evalJson))

        `when`(persistentStorage.loadForKey("testKeyHash", "testAttrsHash")).thenReturn(persistedData)
        `when`(evalSerializer.deserialize(evalJson)).thenReturn(storedEval)

        manager.loadLocal(testEvalKey)

        verify(callbacks).onLoadStarted()
        verify(callbacks).onLoadSucceeded(anyLong())
        verify(callbacks, never()).onLoadFailed(anyString())
        verify(callbacks).onEvalStorageUpdated(testEvalKey, 42L, listOf(storedEval))
    }

    @Test
    fun `loadLocal calls onLoadFailed on exception and returns null`() = scope.runTest {
        `when`(persistentStorage.loadForKey("testKeyHash", "testAttrsHash")).thenThrow(RuntimeException("db error"))

        val result = manager.loadLocal(testEvalKey)

        assertNull(result)
        verify(callbacks).onLoadStarted()
        verify(callbacks).onLoadFailed("db error")
        verify(callbacks, never()).onLoadSucceeded(anyLong())
    }

    // --- persistAsync tests ---

    @Test
    fun `persistAsync serializes and persists evaluations using hash key`() = scope.runTest {
        val storedEval = makeStoredEvaluation()
        val evalJson = """{"result":{"flag":"my_flag","treatment":"on"},"flagSets":["set1"]}"""
        `when`(evalSerializer.serialize(storedEval)).thenReturn(evalJson)

        manager.persistAsync(testEvalKey, 42L, listOf(storedEval))
        advanceUntilIdle()

        val expectedSerializedEval = SerializedEvaluation(flagName = storedEval.result.flag, json = evalJson)
        verify(persistentStorage).persistForKey("testKeyHash", "testAttrsHash", 42L, listOf(expectedSerializedEval))
    }

    @Test
    fun `persistAsync calls onWriteScheduled and onWriteSucceeded`() = scope.runTest {
        val storedEval = makeStoredEvaluation()
        val evalJson = """{"result":{"flag":"my_flag","treatment":"on"},"flagSets":["set1"]}"""
        `when`(evalSerializer.serialize(storedEval)).thenReturn(evalJson)

        manager.persistAsync(testEvalKey, 42L, listOf(storedEval))
        advanceUntilIdle()

        verify(callbacks).onWriteScheduled()
        verify(callbacks).onWriteSucceeded()
    }

    @Test
    fun `persistAsync calls onWriteFailed on exception`() = scope.runTest {
        val storedEval = makeStoredEvaluation()
        `when`(evalSerializer.serialize(storedEval)).thenThrow(RuntimeException("write error"))

        manager.persistAsync(testEvalKey, 42L, listOf(storedEval))
        advanceUntilIdle()

        verify(callbacks).onWriteScheduled()
        verify(callbacks).onWriteFailed("write error")
        verify(callbacks, never()).onWriteSucceeded()
    }
}
