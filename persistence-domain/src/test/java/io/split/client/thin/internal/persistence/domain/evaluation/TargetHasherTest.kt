package io.split.client.thin.internal.persistence.domain.evaluation

import io.split.client.thin.Key
import io.split.client.thin.internal.evaluation.EvaluationKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TargetHasherTest {

    private val hasher = TargetHasher()

    private fun key(matching: String, bucketing: String? = null) =
        EvaluationKey(key = Key(matching, bucketing))

    private fun keyWithAttrs(matching: String, attrs: Map<String, Any?>) =
        EvaluationKey(key = Key(matching), attributes = attrs)

    @Test
    fun `same evalKey produces same hash`() {
        val a = keyWithAttrs("user1", mapOf("age" to 30L, "plan" to "premium"))
        val b = keyWithAttrs("user1", mapOf("age" to 30L, "plan" to "premium"))
        assertEquals(hasher.hash(a), hasher.hash(b))
    }

    @Test
    fun `attribute map order does not affect hash`() {
        val a = keyWithAttrs("user1", mapOf("age" to 30L, "plan" to "premium"))
        val b = keyWithAttrs("user1", mapOf("plan" to "premium", "age" to 30L))
        assertEquals(hasher.hash(a), hasher.hash(b))
    }

    @Test
    fun `different attributes produce different hash`() {
        val a = keyWithAttrs("user1", mapOf("plan" to "free"))
        val b = keyWithAttrs("user1", mapOf("plan" to "premium"))
        assertNotEquals(hasher.hash(a), hasher.hash(b))
    }

    @Test
    fun `different matching keys produce different hash`() {
        val a = key("user1")
        val b = key("user2")
        assertNotEquals(hasher.hash(a), hasher.hash(b))
    }

    @Test
    fun `different bucketing keys produce different hash`() {
        val a = EvaluationKey(Key("user1", "bucket-a"))
        val b = EvaluationKey(Key("user1", "bucket-b"))
        assertNotEquals(hasher.hash(a), hasher.hash(b))
    }

    @Test
    fun `no attributes vs empty attributes produce same hash`() {
        val a = EvaluationKey(Key("user1"))
        val b = EvaluationKey(Key("user1"), emptyMap())
        assertEquals(hasher.hash(a), hasher.hash(b))
    }

    @Test
    fun `hash has fixed length`() {
        val h = hasher.hash(key("user1"))
        assertEquals(16, h.length) // MurmurHash3 x86_128 first 64 bits = 8 bytes = 16 hex chars
    }
}
