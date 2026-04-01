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
    fun `same evalKey produces same keyHash and attrsHash`() {
        val a = keyWithAttrs("user1", mapOf("age" to 30L, "plan" to "premium"))
        val b = keyWithAttrs("user1", mapOf("age" to 30L, "plan" to "premium"))
        assertEquals(hasher.hash(a), hasher.hash(b))
    }

    @Test
    fun `attribute map order does not affect attrsHash`() {
        val a = keyWithAttrs("user1", mapOf("age" to 30L, "plan" to "premium"))
        val b = keyWithAttrs("user1", mapOf("plan" to "premium", "age" to 30L))
        assertEquals(hasher.hash(a).attrsHash, hasher.hash(b).attrsHash)
    }

    @Test
    fun `different attributes produce different attrsHash but same keyHash`() {
        val a = keyWithAttrs("user1", mapOf("plan" to "free"))
        val b = keyWithAttrs("user1", mapOf("plan" to "premium"))
        assertNotEquals(hasher.hash(a).attrsHash, hasher.hash(b).attrsHash)
        assertEquals(hasher.hash(a).keyHash, hasher.hash(b).keyHash)
    }

    @Test
    fun `different matching key produces different keyHash but same attrsHash`() {
        val a = keyWithAttrs("user1", mapOf("plan" to "premium"))
        val b = keyWithAttrs("user2", mapOf("plan" to "premium"))
        assertNotEquals(hasher.hash(a).keyHash, hasher.hash(b).keyHash)
        assertEquals(hasher.hash(a).attrsHash, hasher.hash(b).attrsHash)
    }

    @Test
    fun `different bucketing keys produce different keyHash`() {
        val a = EvaluationKey(Key("user1", "bucket-a"))
        val b = EvaluationKey(Key("user1", "bucket-b"))
        assertNotEquals(hasher.hash(a).keyHash, hasher.hash(b).keyHash)
    }

    @Test
    fun `no attributes vs empty attributes produce same attrsHash`() {
        val a = EvaluationKey(Key("user1"))
        val b = EvaluationKey(Key("user1"), emptyMap())
        assertEquals(hasher.hash(a).attrsHash, hasher.hash(b).attrsHash)
    }

    @Test
    fun `keyHash and attrsHash each have length 16`() {
        val h = hasher.hash(key("user1"))
        assertEquals(16, h.keyHash.length)
        assertEquals(16, h.attrsHash.length)
    }
}
