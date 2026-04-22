package io.split.client.thin.internal.evaluation

import io.split.client.thin.Key
import io.split.client.thin.Target
import io.split.client.thin.internal.secure.EvaluationFilters
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultEvaluationProviderTest {

    private val emptyResponseJson = """{"till": -1, "since": -1, "evaluations": []}"""

    private fun makeProvider(
        responseBody: String? = emptyResponseJson,
        statusCode: Int = 200,
    ): Pair<DefaultEvaluationProvider, FakeSecureHttpClient> {
        val httpClient = FakeSecureHttpClient(responseBody = responseBody, statusCode = statusCode)
        val provider = DefaultEvaluationProvider(
            secureHttpClient = httpClient,
            deserializer = JsonEvaluationResponseDeserializer(),
        )
        return provider to httpClient
    }

    @Test
    fun `calls SecureHttpClient with correct EvaluationTarget mapping`() = runTest {
        val (provider, httpClient) = makeProvider()
        val evalKey = EvaluationKey(Key("user-1", "bucket-1"), mapOf("plan" to "premium"))

        provider.fetch(evalKey, null, -1L)

        val target = httpClient.lastFetchTarget!!
        assertEquals("user-1", target.matchingKey)
        assertEquals("bucket-1", target.bucketingKey)
        assertEquals(mapOf("plan" to "premium"), target.attributes)
    }

    @Test
    fun `maps empty attributes to null in EvaluationTarget`() = runTest {
        val (provider, httpClient) = makeProvider()
        val evalKey = EvaluationKey(Key("user-1"))

        provider.fetch(evalKey, null, -1L)

        val target = httpClient.lastFetchTarget!!
        assertEquals(null, target.attributes)
    }

    @Test
    fun `passes filters through to SecureHttpClient`() = runTest {
        val (provider, httpClient) = makeProvider()
        val evalKey = EvaluationKey(Key("user-1"))
        val filters = EvaluationFilters(flagNames = setOf("flag-a"), flagSets = null)

        provider.fetch(evalKey, filters, -1L)

        assertEquals(filters, httpClient.lastFetchFilters)
    }

    @Test
    fun `deserializes response via deserializer`() = runTest {
        val json = """
            {"till": 42, "since": -1, "evaluations": [
                {"featureName": "my-flag", "treatment": "on", "sets": []}
            ]}
        """.trimIndent()
        val (provider, _) = makeProvider(responseBody = json)
        val evalKey = EvaluationKey(Key("user-1"))

        val result = provider.fetch(evalKey, null, -1L)

        assertEquals(42L, result!!.changeNumber)
        assertEquals(1, result.evaluations.size)
        assertEquals("my-flag", result.evaluations[0].result.flag)
        assertEquals("on", result.evaluations[0].result.treatment)
    }

    @Test
    fun `returns null on 304 not modified`() = runTest {
        val (provider, _) = makeProvider(statusCode = 304)
        val result = provider.fetch(EvaluationKey(Key("user-1")), null, -1L)
        assertEquals(null, result)
    }

    @Test
    fun `returns null on null body without throwing`() = runTest {
        val (provider, _) = makeProvider(responseBody = null)
        val result = provider.fetch(EvaluationKey(Key("user-1")), null, -1L)
        assertEquals(null, result)
    }

    @Test
    fun `returns null on empty body without throwing`() = runTest {
        val (provider, _) = makeProvider(responseBody = "")
        val result = provider.fetch(EvaluationKey(Key("user-1")), null, -1L)
        assertEquals(null, result)
    }

    @Test
    fun `invokes onEmptyResponseBody callback on empty body`() = runTest {
        val httpClient = FakeSecureHttpClient(responseBody = "", statusCode = 200)
        val callbackKeys = mutableListOf<EvaluationKey>()
        val provider = DefaultEvaluationProvider(
            secureHttpClient = httpClient,
            deserializer = JsonEvaluationResponseDeserializer(),
            onEmptyResponseBody = { callbackKeys.add(it) },
        )
        val evalKey = EvaluationKey(Key("user-1"))

        provider.fetch(evalKey, null, -1L)

        assertEquals(listOf(evalKey), callbackKeys)
    }

    @Test
    fun `invokes onEmptyResponseBody callback on null body`() = runTest {
        val httpClient = FakeSecureHttpClient(responseBody = null, statusCode = 200)
        val callbackKeys = mutableListOf<EvaluationKey>()
        val provider = DefaultEvaluationProvider(
            secureHttpClient = httpClient,
            deserializer = JsonEvaluationResponseDeserializer(),
            onEmptyResponseBody = { callbackKeys.add(it) },
        )
        val evalKey = EvaluationKey(Key("user-1"))

        provider.fetch(evalKey, null, -1L)

        assertEquals(listOf(evalKey), callbackKeys)
    }
}

class TargetMappingTest {

    @Test
    fun `matchingKey and bucketingKey mapped correctly`() {
        val evalKey = EvaluationKey(Key("user-1", "bucket-1"))
        val target = evalKey.toEvaluationTarget()
        assertEquals("user-1", target.matchingKey)
        assertEquals("bucket-1", target.bucketingKey)
    }

    @Test
    fun `empty attributes maps to null`() {
        val evalKey = EvaluationKey(Key("user-1"), emptyMap())
        val target = evalKey.toEvaluationTarget()
        assertEquals(null, target.attributes)
    }

    @Test
    fun `non-empty attributes preserved`() {
        val attrs = mapOf("plan" to "premium", "region" to "us-east")
        val evalKey = EvaluationKey(Key("user-1"), attrs)
        val target = evalKey.toEvaluationTarget()
        assertEquals(attrs, target.attributes)
    }

    @Test
    fun `Target toEvaluationKey drops trafficType`() {
        val target = Target(Key("user-1"), mapOf("a" to "b"), trafficType = "user")
        val evalKey = target.toEvaluationKey()
        assertEquals(Key("user-1"), evalKey.key)
        assertEquals(mapOf("a" to "b"), evalKey.attributes)
    }
}
