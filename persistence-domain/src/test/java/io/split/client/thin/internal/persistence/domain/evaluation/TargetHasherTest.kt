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

    @Test
    fun `matchingKey with colon and null bucketingKey does not collide with other splits`() {
        // "user:1" + null → must not equal "user" + "1:null"
        val a = EvaluationKey(Key("user:1", null))
        val b = EvaluationKey(Key("user", "1:null"))
        assertNotEquals(
            "matchingKey='user:1', bucketingKey=null collides with matchingKey='user', bucketingKey='1:null'",
            hasher.hash(a).keyHash,
            hasher.hash(b).keyHash,
        )
    }

    @Test
    fun `attribute key containing equals sign does not collide with other attribute`() {
        // attr key "a=b" with value "c" must not collide with key "a" value "b=c"
        val a = keyWithAttrs("user1", mapOf("a=b" to "c"))
        val b = keyWithAttrs("user1", mapOf("a" to "b=c"))
        assertNotEquals(
            "Attribute key containing '=' aliases different attribute",
            hasher.hash(a).attrsHash,
            hasher.hash(b).attrsHash,
        )
    }

    @Test
    fun `attribute value containing comma does not collide with adjacent attribute`() {
        // key "a" value "x,b=y" must not collide with keys "a"="x", "b"="y"
        val a = keyWithAttrs("user1", mapOf("a" to "x,b=y"))
        val b = keyWithAttrs("user1", mapOf("a" to "x", "b" to "y"))
        assertNotEquals(
            "Attribute value containing ',' aliases adjacent attribute pair",
            hasher.hash(a).attrsHash,
            hasher.hash(b).attrsHash,
        )
    }

    @Test
    fun `attrs with Int 1 and String "1" produce different attrsHash`() {
        val a = keyWithAttrs("user1", mapOf("val" to 1L))
        val b = keyWithAttrs("user1", mapOf("val" to "1"))
        assertNotEquals(
            "Int 1 and String '1' produce same attrsHash (type not preserved)",
            hasher.hash(a).attrsHash,
            hasher.hash(b).attrsHash,
        )
    }

    @Test
    fun `attrs with listOf("a","b") and listOf("a, b") produce different attrsHash`() {
        val a = keyWithAttrs("user1", mapOf("items" to listOf("a", "b")))
        val b = keyWithAttrs("user1", mapOf("items" to listOf("a, b")))
        assertNotEquals(
            "List [a,b] and List [a, b] produce same attrsHash",
            hasher.hash(a).attrsHash,
            hasher.hash(b).attrsHash,
        )
    }
}
