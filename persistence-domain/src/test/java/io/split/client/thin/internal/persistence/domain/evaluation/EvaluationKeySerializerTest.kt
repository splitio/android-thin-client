package io.split.client.thin.internal.persistence.domain.evaluation

import io.split.client.thin.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EvaluationKeySerializerTest {

    private val serializer = EvaluationKeySerializer()

    @Test
    fun `serialize and deserialize key with bucketing key`() {
        val key = Key(matchingKey = "user1", bucketingKey = "bucket1")
        val json = serializer.serialize(key)
        val result = serializer.deserialize(json)
        assertEquals(key, result)
    }

    @Test
    fun `serialize and deserialize key without bucketing key`() {
        val key = Key(matchingKey = "user2")
        val json = serializer.serialize(key)
        val result = serializer.deserialize(json)
        assertEquals("user2", result.matchingKey)
        assertNull(result.bucketingKey)
    }

    @Test
    fun `serialized json contains matchingKey field`() {
        val key = Key(matchingKey = "abc")
        val json = serializer.serialize(key)
        assert(json.contains("matchingKey")) { "Expected matchingKey in JSON: $json" }
    }
}
