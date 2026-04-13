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
    fun `attribute value with double quote produces valid JSON`() {
        // StringBuilder approach emits: {"a":"say "hi""} — invalid JSON
        // Correct output:              {"a":"say \"hi\""}
        val json = ContentDigest.serializeAttributes(mapOf("a" to "say \"hi\""))
        assertEquals("""{"a":"say \"hi\""}""", json)
    }

    @Test
    fun `attribute value with backslash produces valid JSON`() {
        // StringBuilder approach emits: {"a":"C:\Users\foo"} — invalid JSON
        // Correct output:              {"a":"C:\\Users\\foo"}
        val json = ContentDigest.serializeAttributes(mapOf("a" to "C:\\Users\\foo"))
        assertEquals("""{"a":"C:\\Users\\foo"}""", json)
    }

    @Test
    fun `attribute value that is a list serializes as JSON array`() {
        // List falls into else→toString() with StringBuilder: produces "[a, b]" instead of ["a","b"]
        val json = ContentDigest.serializeAttributes(mapOf("perms" to listOf("read", "write")))
        assertEquals("""{"perms":["read","write"]}""", json)
    }

    @Test
    fun `list with mixed primitives serializes correctly`() {
        val json = ContentDigest.serializeAttributes(mapOf("vals" to listOf("x", 42, true)))
        assertEquals("""{"vals":["x",42,true]}""", json)
    }

    @Test
    fun `null elements in list are omitted`() {
        // Other SDK implementations (Swift/JS) have no null type in lists — omit for consistency
        val json = ContentDigest.serializeAttributes(mapOf("vals" to listOf("a", null, "b")))
        assertEquals("""{"vals":["a","b"]}""", json)
    }

    @Test
    fun `known input produces expected digest`() {
        // Pinned value for "user1:{}" with Murmur3-128x86, first half (h1+h2), Base64 no-padding.
        // If this fails the hash algorithm or serialization has changed — update deliberately.
        val target = EvaluationTarget(matchingKey = "user1", bucketingKey = null, attributes = null)
        assertEquals("UjEmUGAWceM", ContentDigest.compute(target))
    }
}
