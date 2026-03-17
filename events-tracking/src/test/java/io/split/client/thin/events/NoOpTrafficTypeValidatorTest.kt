package io.split.client.thin.events

import org.junit.Assert.assertTrue
import org.junit.Test

class NoOpTrafficTypeValidatorTest {

    private val validator = NoOpTrafficTypeValidator()

    @Test
    fun `isValid returns true for any non-null traffic type`() {
        assertTrue(validator.isValid("user"))
        assertTrue(validator.isValid("account"))
        assertTrue(validator.isValid(""))
    }

    @Test
    fun `isValid returns true for null traffic type`() {
        assertTrue(validator.isValid(null))
    }
}
