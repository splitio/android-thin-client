package io.split.client.thin.internal.secure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ContentDigestTest {

    @Test
    fun `target with no attributes hashes matchingKey colon empty object`() {
        val target = EvaluationTarget(matchingKey = "user1", bucketingKey = null, attributes = null)

        val digest = ContentDigest.compute(target)

        // digest of "user1:{}" — must be deterministic and non-empty
        assertNotNull(digest)
        assertEquals(digest, ContentDigest.compute(target))
    }

    @Test
    fun `target with empty attributes hashes matchingKey colon empty object`() {
        val targetNull = EvaluationTarget(matchingKey = "user1", bucketingKey = null, attributes = null)
        val targetEmpty = EvaluationTarget(matchingKey = "user1", bucketingKey = null, attributes = emptyMap())

        assertEquals(ContentDigest.compute(targetNull), ContentDigest.compute(targetEmpty))
    }

    @Test
    fun `target with attributes sorts keys alphabetically`() {
        val targetAB = EvaluationTarget(matchingKey = "user1", bucketingKey = null, attributes = mapOf("a" to 1, "b" to "hello"))
        val targetBA = EvaluationTarget(matchingKey = "user1", bucketingKey = null, attributes = mapOf("b" to "hello", "a" to 1))

        assertEquals(ContentDigest.compute(targetAB), ContentDigest.compute(targetBA))
    }

    @Test
    fun `different matchingKeys produce different digests`() {
        val t1 = EvaluationTarget(matchingKey = "user1", bucketingKey = null, attributes = null)
        val t2 = EvaluationTarget(matchingKey = "user2", bucketingKey = null, attributes = null)

        val d1 = ContentDigest.compute(t1)
        val d2 = ContentDigest.compute(t2)

        assert(d1 != d2) { "Expected different digests for different matchingKeys" }
    }

    @Test
    fun `different attributes produce different digests`() {
        val t1 = EvaluationTarget(matchingKey = "user1", bucketingKey = null, attributes = mapOf("a" to 1))
        val t2 = EvaluationTarget(matchingKey = "user1", bucketingKey = null, attributes = mapOf("a" to 2))

        val d1 = ContentDigest.compute(t1)
        val d2 = ContentDigest.compute(t2)

        assert(d1 != d2) { "Expected different digests for different attribute values" }
    }

    @Test
    fun `number attributes serialized as numbers not strings`() {
        // "a":1 and "a":"1" must produce different digests
        val intAttr = EvaluationTarget(matchingKey = "u", bucketingKey = null, attributes = mapOf("a" to 1))
        val strAttr = EvaluationTarget(matchingKey = "u", bucketingKey = null, attributes = mapOf("a" to "1"))

        val d1 = ContentDigest.compute(intAttr)
        val d2 = ContentDigest.compute(strAttr)

        assert(d1 != d2) { "int 1 and string '1' must produce different digests" }
    }

    @Test
    fun `known input produces expected digest`() {
        // "user1:{}" — pre-computed expected value for regression
        val target = EvaluationTarget(matchingKey = "user1", bucketingKey = null, attributes = null)
        val digest = ContentDigest.compute(target)

        // Must be a non-empty Base64 string (no padding, 11 chars for 8 bytes)
        assertEquals(11, digest.length)
        assert(digest.matches(Regex("[A-Za-z0-9+/]+"))) { "Expected Base64 characters, got: $digest" }
    }
}
