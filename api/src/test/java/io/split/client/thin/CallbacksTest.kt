package io.split.client.thin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallbacksTest {

    @Test
    fun `split callback receives result and error`() {
        var receivedResult: String? = null
        var receivedError: Throwable? = null

        val callback = SplitCallback<String> { result, error ->
            receivedResult = result
            receivedError = error
        }

        callback.onComplete("ok", null)
        assertEquals("ok", receivedResult)
        assertNull(receivedError)
    }

    @Test
    fun `split void callback receives error`() {
        var receivedError: Throwable? = null
        val expected = IllegalStateException("boom")

        val callback = SplitVoidCallback { error ->
            receivedError = error
        }

        callback.onComplete(expected)
        assertEquals(expected, receivedError)
    }
}
