package io.split.client.thin.internal.evaluation

import io.split.client.thin.Key
import io.split.client.thin.internal.observer.ObservableEventType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationFactoryTest {

    private val emptyResponseJson = """{"till": -1, "since": -1, "evaluations": []}"""
    private val evalKey = EvaluationKey(Key("user-1"))

    private fun makeComponents(
        responseBody: String? = emptyResponseJson,
        throwOnFetch: Throwable? = null,
    ): Pair<EvaluationComponents, FakeCompositeObserver> {
        val observer = FakeCompositeObserver()
        val httpClient = FakeSecureHttpClient(responseBody = responseBody, throwOnFetch = throwOnFetch)
        val components = createEvaluationComponents(httpClient, observer)
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
    fun `TARGET_SWITCH reason fires EVAL_STORAGE_UPDATED`() = runTest {
        val (components, observer) = makeComponents()

        components.fetchCoordinator.fetchIfNeeded(evalKey, null, FetchReason.TARGET_SWITCH)

        assertTrue(observer.capturedEvents.any { it.type == ObservableEventType.EVAL_STORAGE_UPDATED })
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
}
