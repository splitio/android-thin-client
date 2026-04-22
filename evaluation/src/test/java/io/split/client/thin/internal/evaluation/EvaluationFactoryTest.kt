package io.split.client.thin.internal.evaluation

import io.split.client.thin.EvaluationResult
import io.split.client.thin.Key
import io.split.client.thin.Target
import io.split.client.thin.internal.observer.ObservableEventType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationFactoryTest {

    private val emptyResponseJson = """{"till": -1, "since": -1, "evaluations": []}"""
    private val evalKey = EvaluationKey(Key("user-1"))

    private fun makeComponents(
        responseBody: String? = emptyResponseJson,
        throwOnFetch: Throwable? = null,
        cacheLoader: EvaluationCacheLoader? = null,
    ): Pair<EvaluationComponents, FakeCompositeObserver> {
        val observer = FakeCompositeObserver()
        val httpClient = FakeSecureHttpClient(responseBody = responseBody, throwOnFetch = throwOnFetch)
        val components = createEvaluationComponents(httpClient, observer, cacheLoader)
        return components to observer
    }

    @Test
    fun `createEvaluationComponents returns components with wired collaborators`() {
        val (components, _) = makeComponents()
        assertNotNull(components.fetchCoordinator)
        assertNotNull(components.repository)
    }

    @Test
    fun `successful fetch fires EVAL_FETCH_REQUESTED with matchingKey and reason`() = runTest {
        val (components, observer) = makeComponents()

        components.fetchCoordinator.fetchIfNeeded(evalKey, null, FetchReason.INITIALIZATION)

        val event = observer.capturedEvents.first { it.type == ObservableEventType.EVAL_FETCH_REQUESTED }
        assertEquals("user-1", event.properties["matchingKey"])
        assertEquals("INITIALIZATION", event.properties["reason"])
    }

    @Test
    fun `successful fetch fires EVAL_FETCH_STARTED with matchingKey`() = runTest {
        val (components, observer) = makeComponents()

        components.fetchCoordinator.fetchIfNeeded(evalKey, null, FetchReason.INITIALIZATION)

        val event = observer.capturedEvents.first { it.type == ObservableEventType.EVAL_FETCH_STARTED }
        assertEquals("user-1", event.properties["matchingKey"])
    }

    @Test
    fun `successful fetch fires EVAL_FETCH_SUCCEEDED with matchingKey`() = runTest {
        val (components, observer) = makeComponents()

        components.fetchCoordinator.fetchIfNeeded(evalKey, null, FetchReason.INITIALIZATION)

        val event = observer.capturedEvents.first { it.type == ObservableEventType.EVAL_FETCH_SUCCEEDED }
        assertEquals("user-1", event.properties["matchingKey"])
    }

    @Test
    fun `INITIALIZATION reason fires EVAL_STORAGE_UPDATED`() = runTest {
        val (components, observer) = makeComponents()

        components.fetchCoordinator.fetchIfNeeded(evalKey, null, FetchReason.INITIALIZATION)

        assertTrue(observer.capturedEvents.any { it.type == ObservableEventType.EVAL_STORAGE_UPDATED })
    }

    @Test
    fun `TARGET_SWITCH reason fires EVALUATIONS_UPDATED`() = runTest {
        val (components, observer) = makeComponents()

        components.fetchCoordinator.fetchIfNeeded(evalKey, null, FetchReason.TARGET_SWITCH)

        assertTrue(observer.capturedEvents.any { it.type == ObservableEventType.EVALUATIONS_UPDATED })
    }

    @Test
    fun `PERIODIC reason fires EVALUATIONS_UPDATED`() = runTest {
        val (components, observer) = makeComponents()

        components.fetchCoordinator.fetchIfNeeded(evalKey, null, FetchReason.PERIODIC)

        assertTrue(observer.capturedEvents.any { it.type == ObservableEventType.EVALUATIONS_UPDATED })
    }

    @Test
    fun `PUSH reason fires EVALUATIONS_UPDATED`() = runTest {
        val (components, observer) = makeComponents()

        components.fetchCoordinator.fetchIfNeeded(evalKey, null, FetchReason.PUSH)

        assertTrue(observer.capturedEvents.any { it.type == ObservableEventType.EVALUATIONS_UPDATED })
    }

    @Test
    fun `failed fetch fires EVAL_FETCH_FAILED with matchingKey and error message`() = runTest {
        val error = RuntimeException("network error")
        val (components, observer) = makeComponents(throwOnFetch = error)

        runCatching { components.fetchCoordinator.fetchIfNeeded(evalKey, null, FetchReason.INITIALIZATION) }

        val event = observer.capturedEvents.first { it.type == ObservableEventType.EVAL_FETCH_FAILED }
        assertEquals("user-1", event.properties["matchingKey"])
        assertEquals("network error", event.properties["error"])
    }

    @Test
    fun `deserialization failure fires EVAL_DESERIALIZE_FAILED with matchingKey`() = runTest {
        val (components, observer) = makeComponents(responseBody = "not-valid-json")

        runCatching { components.fetchCoordinator.fetchIfNeeded(evalKey, null, FetchReason.INITIALIZATION) }

        val event = observer.capturedEvents.first { it.type == ObservableEventType.EVAL_DESERIALIZE_FAILED }
        assertEquals("user-1", event.properties["matchingKey"])
        assertNotNull(event.properties["error"])
    }

    @Test
    fun `storage shared between fetchCoordinator and repository`() = runTest {
        val responseJson = """
            {"till": 1, "since": -1, "evaluations": [
                {"featureName": "my-flag", "treatment": "on", "sets": []}
            ]}
        """.trimIndent()
        val (components, _) = makeComponents(responseBody = responseJson)

        components.fetchCoordinator.fetchIfNeeded(evalKey, null, FetchReason.INITIALIZATION)
        val result = components.repository.getTreatment(evalKey, "my-flag")

        assertNotNull(result)
        assertEquals("my-flag", result?.result?.flag)
        assertEquals("on", result?.result?.treatment)
    }

    @Test
    fun `cacheLoader loadLocal is called when wired via createEvaluationComponents`() = runTest {
        val cachedChange = EvaluationChange(evalKey, 5L, listOf(StoredEvaluation(EvaluationResult(flag = "cached-flag", treatment = "off"))))
        val loadCalls = mutableListOf<EvaluationKey>()
        val loader = object : EvaluationCacheLoader {
            override suspend fun loadLocal(evalKey: EvaluationKey): CacheLoadResult? {
                loadCalls.add(evalKey)
                return CacheLoadResult(cachedChange, null)
            }
            override fun persistAsync(evalKey: EvaluationKey, changeNumber: Long, evaluations: List<StoredEvaluation>) = Unit
        }

        val (components, _) = makeComponents(cacheLoader = loader)

        components.repository.setTarget(Target(Key("user-1"), trafficType = "user"), null, isInitialization = false)

        assertEquals(1, loadCalls.size)
        assertEquals(evalKey, loadCalls[0])
    }

    @Test
    fun `cacheLoader persistAsync is called after successful fetch via createEvaluationComponents`() = runTest {
        val persistCalls = mutableListOf<EvaluationKey>()
        val loader = object : EvaluationCacheLoader {
            override suspend fun loadLocal(evalKey: EvaluationKey): CacheLoadResult? = null
            override fun persistAsync(evalKey: EvaluationKey, changeNumber: Long, evaluations: List<StoredEvaluation>) {
                persistCalls.add(evalKey)
            }
        }

        val (components, _) = makeComponents(
            responseBody = """{"till": 1, "since": -1, "evaluations": [{"featureName": "f", "treatment": "on", "sets": []}]}""",
            cacheLoader = loader,
        )

        components.fetchCoordinator.fetchIfNeeded(evalKey, null, FetchReason.INITIALIZATION)

        assertEquals(1, persistCalls.size)
        assertEquals(evalKey, persistCalls[0])
    }

    @Test
    fun `EVAL_LOADED_FROM_STORAGE fires when cache is loaded successfully`() = runTest {
        val cachedChange = EvaluationChange(
            evalKey,
            5L,
            listOf(StoredEvaluation(EvaluationResult(flag = "cached-flag", treatment = "off")))
        )
        val loader = object : EvaluationCacheLoader {
            override suspend fun loadLocal(evalKey: EvaluationKey): CacheLoadResult? = CacheLoadResult(cachedChange, null)
            override fun persistAsync(evalKey: EvaluationKey, changeNumber: Long, evaluations: List<StoredEvaluation>) = Unit
        }

        val (components, observer) = makeComponents(cacheLoader = loader)

        components.repository.setTarget(Target(Key("user-1"), trafficType = "user"), null, isInitialization = false)

        val event = observer.capturedEvents.firstOrNull { it.type == ObservableEventType.EVAL_LOADED_FROM_STORAGE }
        assertNotNull(event)
        assertEquals("user-1", event?.properties?.get("matchingKey"))
    }

    @Test
    fun `EVAL_LOADED_FROM_STORAGE does not fire when loadLocal returns null`() = runTest {
        val loader = object : EvaluationCacheLoader {
            override suspend fun loadLocal(evalKey: EvaluationKey): CacheLoadResult? = null
            override fun persistAsync(evalKey: EvaluationKey, changeNumber: Long, evaluations: List<StoredEvaluation>) = Unit
        }

        val (components, observer) = makeComponents(cacheLoader = loader)

        components.repository.setTarget(Target(Key("user-1"), trafficType = "user"), null, isInitialization = false)

        val event = observer.capturedEvents.firstOrNull { it.type == ObservableEventType.EVAL_LOADED_FROM_STORAGE }
        assertEquals(null, event)
    }

    // -------------------------------------------------------------------------
    // Metadata payload tests
    // -------------------------------------------------------------------------

    private fun makeComponentsWithPayloadBuilders(
        responseBody: String? = emptyResponseJson,
        throwOnFetch: Throwable? = null,
        cacheLoader: EvaluationCacheLoader? = null,
    ): Pair<EvaluationComponents, FakeCompositeObserver> {
        val observer = FakeCompositeObserver()
        val httpClient = FakeSecureHttpClient(responseBody = responseBody, throwOnFetch = throwOnFetch)
        val components = createEvaluationComponents(
            httpClient, observer, cacheLoader,
            cacheLoadedPayloadBuilder = { evalKey, ts -> mapOf("type" to "CACHE_LOADED", "ts" to ts) },
            evaluationsUpdatedPayloadBuilder = { evalKey, reason, names, _ ->
                mapOf("type" to reason.name, "names" to names)
            },
        )
        return components to observer
    }

    @Test
    fun `EVAL_LOADED_FROM_STORAGE event carries payload from cacheLoadedPayloadBuilder`() = runTest {
        val cachedChange = EvaluationChange(
            evalKey, 5L, listOf(StoredEvaluation(EvaluationResult(flag = "f", treatment = "on")))
        )
        val loader = object : EvaluationCacheLoader {
            override suspend fun loadLocal(evalKey: EvaluationKey): CacheLoadResult? = CacheLoadResult(cachedChange, null)
            override fun persistAsync(evalKey: EvaluationKey, changeNumber: Long, evaluations: List<StoredEvaluation>) = Unit
        }
        val (components, observer) = makeComponentsWithPayloadBuilders(cacheLoader = loader)

        components.repository.setTarget(Target(Key("user-1"), trafficType = "user"), null, isInitialization = false)

        val event = observer.capturedEvents.first { it.type == ObservableEventType.EVAL_LOADED_FROM_STORAGE }
        assertNotNull(event.payload)
        @Suppress("UNCHECKED_CAST")
        val payload = event.payload as Map<String, Any?>
        assertEquals("CACHE_LOADED", payload["type"])
    }

    @Test
    fun `EVAL_STORAGE_UPDATED event carries payload from evaluationsUpdatedPayloadBuilder on INITIALIZATION`() = runTest {
        val (components, observer) = makeComponentsWithPayloadBuilders()

        components.fetchCoordinator.fetchIfNeeded(evalKey, null, FetchReason.INITIALIZATION)

        val event = observer.capturedEvents.first { it.type == ObservableEventType.EVAL_STORAGE_UPDATED }
        assertNotNull(event.payload)
        @Suppress("UNCHECKED_CAST")
        val payload = event.payload as Map<String, Any?>
        assertEquals("INITIALIZATION", payload["type"])
    }

    @Test
    fun `EVALUATIONS_UPDATED event carries payload from evaluationsUpdatedPayloadBuilder on PERIODIC`() = runTest {
        val responseWithFlags = """{"till": 1, "since": -1, "evaluations": [{"featureName": "flag-a", "treatment": "on", "sets": []}]}"""
        val (components, observer) = makeComponentsWithPayloadBuilders(responseBody = responseWithFlags)

        components.fetchCoordinator.fetchIfNeeded(evalKey, null, FetchReason.PERIODIC)

        val event = observer.capturedEvents.first { it.type == ObservableEventType.EVALUATIONS_UPDATED }
        assertNotNull(event.payload)
        @Suppress("UNCHECKED_CAST")
        val payload = event.payload as Map<String, Any?>
        assertEquals("PERIODIC", payload["type"])
    }

    @Test
    fun `EVALUATIONS_UPDATED is NOT emitted when changedFlagNames is empty on PERIODIC`() = runTest {
        // emptyResponseJson returns no evaluations, so changedFlagNames will be empty on a re-fetch
        val (components, observer) = makeComponentsWithPayloadBuilders()
        // First fetch so it's not isFirstFetch
        components.fetchCoordinator.fetchIfNeeded(evalKey, null, FetchReason.INITIALIZATION)
        observer.capturedEvents.clear()

        components.fetchCoordinator.fetchIfNeeded(evalKey, null, FetchReason.PERIODIC)

        assertFalse(observer.capturedEvents.any { it.type == ObservableEventType.EVALUATIONS_UPDATED })
    }

    @Test
    fun `EVAL_STORAGE_UPDATED isInitialCacheLoad=true when no cache was loaded before INITIALIZATION sync`() = runTest {
        var capturedIsInitial: Boolean? = null
        val observer = FakeCompositeObserver()
        val httpClient = FakeSecureHttpClient(responseBody = emptyResponseJson)
        createEvaluationComponents(
            httpClient, observer, cacheLoader = null,
            cacheLoadedPayloadBuilder = { _, _ -> null },
            evaluationsUpdatedPayloadBuilder = { _, reason, _, isCacheLoaded ->
                if (reason == FetchReason.INITIALIZATION) capturedIsInitial = isCacheLoaded
                null
            },
        ).fetchCoordinator.fetchIfNeeded(evalKey, null, FetchReason.INITIALIZATION)

        assertEquals(false, capturedIsInitial)
    }

    @Test
    fun `EVAL_STORAGE_UPDATED isInitialCacheLoad=false when cache was loaded before INITIALIZATION sync`() = runTest {
        var capturedCacheLoaded = false
        val cachedChange = EvaluationChange(evalKey, 5L, listOf(StoredEvaluation(EvaluationResult(flag = "f", treatment = "on"))))
        val loader = object : EvaluationCacheLoader {
            override suspend fun loadLocal(evalKey: EvaluationKey): CacheLoadResult? = CacheLoadResult(cachedChange, null)
            override fun persistAsync(evalKey: EvaluationKey, changeNumber: Long, evaluations: List<StoredEvaluation>) = Unit
        }
        var capturedIsInitial: Boolean? = null
        val observer = FakeCompositeObserver()
        val httpClient = FakeSecureHttpClient(responseBody = emptyResponseJson)
        val components = createEvaluationComponents(
            httpClient, observer, cacheLoader = loader,
            cacheLoadedPayloadBuilder = { _, _ ->
                capturedCacheLoaded = true
                null
            },
            evaluationsUpdatedPayloadBuilder = { _, reason, _, isCacheLoaded ->
                if (reason == FetchReason.INITIALIZATION) capturedIsInitial = isCacheLoaded
                null
            },
        )
        components.repository.setTarget(Target(Key("user-1"), trafficType = "user"), null, isInitialization = true)

        assertTrue(capturedCacheLoaded)
        assertEquals(true, capturedIsInitial)
    }
}
