package io.split.client.thin.internal

import io.split.client.thin.Key
import io.split.client.thin.SdkKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidatorTest {

    // validateSdkKey

    @Test
    fun `validateSdkKey returns false for blank sdkKey`() {
        assertFalse(DefaultInputValidator().validateSdkKey(SdkKey("")))
    }

    @Test
    fun `validateSdkKey returns false for whitespace-only sdkKey`() {
        assertFalse(DefaultInputValidator().validateSdkKey(SdkKey("   ")))
    }

    @Test
    fun `validateSdkKey returns true for valid sdkKey`() {
        assertTrue(DefaultInputValidator().validateSdkKey(SdkKey("my-valid-sdk-key")))
    }

    // validateKey — matchingKey

    @Test
    fun `validateKey returns false for blank matchingKey`() {
        assertFalse(DefaultInputValidator().validateKey(Key("")))
    }

    @Test
    fun `validateKey returns false for whitespace-only matchingKey`() {
        assertFalse(DefaultInputValidator().validateKey(Key("   ")))
    }

    @Test
    fun `validateKey returns true for matchingKey exactly 250 chars`() {
        val key250 = "a".repeat(250)
        assertTrue(DefaultInputValidator().validateKey(Key(key250)))
    }

    @Test
    fun `validateKey returns false for matchingKey 251 chars`() {
        val key251 = "a".repeat(251)
        assertFalse(DefaultInputValidator().validateKey(Key(key251)))
    }

    @Test
    fun `validateKey returns true for valid matchingKey with null bucketingKey`() {
        assertTrue(DefaultInputValidator().validateKey(Key("user-1", null)))
    }

    @Test
    fun `validateKey returns true for valid matchingKey with valid bucketingKey`() {
        assertTrue(DefaultInputValidator().validateKey(Key("user-1", "bucket-1")))
    }

    @Test
    fun `validateKey returns false for blank bucketingKey`() {
        assertFalse(DefaultInputValidator().validateKey(Key("user-1", "")))
    }

    @Test
    fun `validateKey returns false for whitespace-only bucketingKey`() {
        assertFalse(DefaultInputValidator().validateKey(Key("user-1", "   ")))
    }

    @Test
    fun `validateKey returns false for 251-char bucketingKey`() {
        val bk251 = "b".repeat(251)
        assertFalse(DefaultInputValidator().validateKey(Key("user-1", bk251)))
    }

    @Test
    fun `validateKey returns true for bucketingKey exactly 250 chars`() {
        val bk250 = "b".repeat(250)
        assertTrue(DefaultInputValidator().validateKey(Key("user-1", bk250)))
    }

    // validateFlagName

    @Test
    fun `validateFlagName returns false for blank flag name`() {
        assertFalse(DefaultInputValidator().validateFlagName(""))
    }

    @Test
    fun `validateFlagName returns false for whitespace-only flag name`() {
        assertFalse(DefaultInputValidator().validateFlagName("   "))
    }

    @Test
    fun `validateFlagName returns true for valid flag name`() {
        assertTrue(DefaultInputValidator().validateFlagName("my_flag"))
    }

    @Test
    fun `validateFlagName returns true for flag name with surrounding whitespace`() {
        assertTrue(DefaultInputValidator().validateFlagName("  my_flag  "))
    }

    // validateEventValue

    @Test
    fun `validateEventValue returns true for finite value and props`() {
        assertTrue(DefaultInputValidator().validateEventValue(1.5, mapOf("a" to 2.0, "b" to "x")))
    }

    @Test
    fun `validateEventValue returns false for NaN value`() {
        assertFalse(DefaultInputValidator().validateEventValue(Double.NaN, null))
    }

    @Test
    fun `validateEventValue returns false for positive infinity value`() {
        assertFalse(DefaultInputValidator().validateEventValue(Double.POSITIVE_INFINITY, null))
    }

    @Test
    fun `validateEventValue returns false when property is Double NaN`() {
        assertFalse(DefaultInputValidator().validateEventValue(1.0, mapOf("p" to Double.NaN)))
    }

    @Test
    fun `validateEventValue returns false when property is Float infinity`() {
        assertFalse(DefaultInputValidator().validateEventValue(1.0, mapOf("p" to Float.POSITIVE_INFINITY)))
    }

    @Test
    fun `validateEventValue returns true for null value and null props`() {
        assertTrue(DefaultInputValidator().validateEventValue(null, null))
    }

    @Test
    fun `validateEventValue returns true for null value and finite props`() {
        assertTrue(DefaultInputValidator().validateEventValue(null, mapOf("p" to 3.0)))
    }
}
