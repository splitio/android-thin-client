package io.split.client.thin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class KeyTest {

    @Test
    fun `key equality`() {
        val key1 = Key(matchingKey = "key1")
        val key2 = Key(matchingKey = "key1")

        assertEquals("Instances should be equal", key1, key2)
        assertEquals("Hashcodes should be equal", key1.hashCode(), key2.hashCode())
        assertEquals("toString should be equal", key1.toString(), key2.toString())
    }

    @Test
    fun `key inequality with different bucketing key`() {
        val key1 = Key(matchingKey = "key1", bucketingKey = "bkey1")
        val key2 = Key(matchingKey = "key1", bucketingKey = "bkey2")
        
        assertFalse("Instances should not be equal", key1 == key2)
        assertNotEquals("Hashcodes should not be equal", key1.hashCode(), key2.hashCode())
        assertNotEquals("toString should not be equal", key1.toString(), key2.toString())
    }

    @Test
    fun `key inequality with different matching key`() {
        val key1 = Key(matchingKey = "key1", bucketingKey = "bkey1")
        val key2 = Key(matchingKey = "key2", bucketingKey = "bkey1")
        
        assertFalse("Instances should not be equal", key1 == key2)
        assertNotEquals("Hashcodes should not be equal", key1.hashCode(), key2.hashCode())
        assertNotEquals("toString should not be equal", key1.toString(), key2.toString())
    }

    @Test
    fun `key inequality with different bucketing key and matching key`() {
        val key1 = Key(matchingKey = "key1", bucketingKey = "bkey1")
        val key2 = Key(matchingKey = "key2", bucketingKey = "bkey2")
        
        assertFalse("Instances should not be equal", key1 == key2)
        assertNotEquals("Hashcodes should not be equal", key1.hashCode(), key2.hashCode())
        assertNotEquals("toString should not be equal", key1.toString(), key2.toString())
    }

    @Test
    fun `key equality with null bucketing key`() {
        val key1 = Key(matchingKey = "key1")
        val key2 = Key(matchingKey = "key1", bucketingKey = null)
        
        assertEquals("Instances should be equal", key1, key2)
        assertEquals("Hashcodes should be equal", key1.hashCode(), key2.hashCode())
        assertEquals("toString should be equal", key1.toString(), key2.toString())
    }
    
    @Test
    fun `key inequality with null bucketing key`() {
        val key1 = Key(matchingKey = "key1")
        val key2 = Key(matchingKey = "key1", bucketingKey = "bkey2")
        
        assertFalse("Instances should not be equal", key1 == key2)
        assertNotEquals("Hashcodes should not be equal", key1.hashCode(), key2.hashCode())
        assertNotEquals("toString should not be equal", key1.toString(), key2.toString())
    }

    @Test
    fun `matching key getter`() {
        val key = Key(matchingKey = "key1")
        assertEquals("Matching key should be equal", "key1", key.matchingKey)
    }

    @Test
    fun `bucketing key getter`() {
        val key = Key(matchingKey = "key1", bucketingKey = "bkey1")
        assertEquals("Bucketing key should be equal", "bkey1", key.bucketingKey)
    }
}
