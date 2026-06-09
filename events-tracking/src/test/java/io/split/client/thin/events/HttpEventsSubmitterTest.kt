package io.split.client.thin.events

import io.split.android.client.submitter.RecorderException
import io.split.client.thin.http.contracts.HttpResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpEventsSubmitterTest {

    private var capturedPayload: String? = null
    private var shouldSucceed = true
    private var statusCode = 200
    private lateinit var submitter: HttpEventsSubmitter

    @Before
    fun setUp() {
        capturedPayload = null
        shouldSucceed = true
        statusCode = 200
        submitter = HttpEventsSubmitter { payload ->
            capturedPayload = payload
            createResponse(shouldSucceed, statusCode)
        }
    }

    @Test
    fun `execute calls postEvents lambda with payload`() {
        val payload = """[{"key":"test"}]"""

        submitter.execute(payload)

        assertEquals(payload, capturedPayload)
    }

    @Test
    fun `execute succeeds when response is 2xx`() {
        shouldSucceed = true
        statusCode = 200

        submitter.execute("""[{"key":"test"}]""")

        // Should not throw
    }

    @Test(expected = RecorderException::class)
    fun `execute throws RecorderException on non-2xx response`() {
        shouldSucceed = false
        statusCode = 500

        submitter.execute("""[{"key":"test"}]""")
    }

    @Test(expected = RecorderException::class)
    fun `execute wraps network exception as RecorderException`() {
        val throwingSubmitter = HttpEventsSubmitter { throw RuntimeException("connection refused") }

        throwingSubmitter.execute("""[{"key":"test"}]""")
    }

    @Test(expected = CancellationException::class)
    fun `execute rethrows CancellationException`() {
        val throwingSubmitter = HttpEventsSubmitter { throw CancellationException("cancelled") }

        throwingSubmitter.execute("""[{"key":"test"}]""")
    }

    @Test
    fun `exception contains correct status code`() {
        shouldSucceed = false
        statusCode = 400

        try {
            submitter.execute("""[{"key":"test"}]""")
        } catch (e: RecorderException) {
            assertEquals(400, e.httpStatus)
        }
    }

    @Test
    fun `exception is not retryable for 5xx errors`() {
        shouldSucceed = false
        statusCode = 503

        try {
            submitter.execute("""[{"key":"test"}]""")
        } catch (e: RecorderException) {
            assertTrue(!e.isRetryable)
        }
    }

    @Test
    fun `exception is not retryable for 4xx errors`() {
        shouldSucceed = false
        statusCode = 400

        try {
            submitter.execute("""[{"key":"test"}]""")
        } catch (e: RecorderException) {
            assertTrue(!e.isRetryable)
        }
    }

    private fun createResponse(success: Boolean, status: Int): HttpResponse {
        return object : HttpResponse {
            override val isSuccess = success
            override val httpStatus = status
            override fun getData() = ""
        }
    }
}
