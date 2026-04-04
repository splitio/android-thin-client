package io.split.client.thin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetTest {

    @Test
    fun `target constructor with matching key and traffic type`() {
        val target = Target(key = Key("test-target", bucketingKey = null), trafficType = "user")
        assertEquals("test-target", target.key.matchingKey)
        assertEquals("user", target.trafficType)
        assertTrue(target.attributes.isEmpty())
    }

    @Test
    fun `target constructor with matching key and bucketing key`() {
        val target = Target(Key("test-target", "test-bucketing-key"), trafficType = "user")
        assertEquals("test-target", target.key.matchingKey)
        assertEquals("test-bucketing-key", target.key.bucketingKey)
        assertEquals("user", target.trafficType)
        assertTrue(target.attributes.isEmpty())
    }

    @Test
    fun `target constructor with all fields`() {
        val attributes = mapOf("country" to "ar", "plan" to "premium")
        val target = Target(
            key = Key("test-target", "test-bucketing-key"),
            attributes = attributes,
            trafficType = "account",
        )

        assertEquals("test-target", target.key.matchingKey)
        assertEquals("test-bucketing-key", target.key.bucketingKey)
        assertEquals(attributes, target.attributes)
        assertEquals("account", target.trafficType)
    }

    @Test
    fun `target equality`() {
        val target1 = Target(
            key = Key("test-target", "test-bucketing-key"),
            attributes = mapOf("country" to "ar"),
            trafficType = "account",
        )
        val target2 = Target(
            key = Key("test-target", "test-bucketing-key"),
            attributes = mapOf("country" to "ar"),
            trafficType = "account",
        )

        assertEquals(target1, target2)
        assertEquals(target1.hashCode(), target2.hashCode())
        assertEquals(target1.toString(), target2.toString())
    }

    @Test
    fun `target inequality with different key`() {
        val target1 = Target(Key("test-target-1"), trafficType = "user")
        val target2 = Target(Key("test-target-2"), trafficType = "user")

        assertNotEquals(target1, target2)
    }

    @Test
    fun `target inequality with different attributes`() {
        val target1 = Target(Key("test-target"), attributes = mapOf("country" to "ar"), trafficType = "user")
        val target2 = Target(Key("test-target"), attributes = mapOf("country" to "us"), trafficType = "user")

        assertNotEquals(target1, target2)
    }

    @Test
    fun `target inequality with different traffic type`() {
        val target1 = Target(Key("test-target"), trafficType = "user")
        val target2 = Target(Key("test-target"), trafficType = "account")

        assertNotEquals(target1, target2)
    }
}
