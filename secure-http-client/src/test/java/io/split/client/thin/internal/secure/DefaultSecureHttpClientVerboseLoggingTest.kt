package io.split.client.thin.internal.secure

import io.split.android.client.utils.logger.LogPrinter
import io.split.android.client.utils.logger.Logger
import io.split.android.client.utils.logger.SplitLogLevel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DefaultSecureHttpClientVerboseLoggingTest {

    private val verboseMessages = mutableListOf<String>()

    private val capturingPrinter = object : LogPrinter {
        override fun v(tag: String, msg: String, tr: Throwable?) { verboseMessages.add(msg) }
        override fun d(tag: String, msg: String, tr: Throwable?) {}
        override fun i(tag: String, msg: String, tr: Throwable?) {}
        override fun w(tag: String, msg: String, tr: Throwable?) {}
        override fun e(tag: String, msg: String, tr: Throwable?) {}
        override fun wtf(tag: String, msg: String, tr: Throwable?) {}
    }

    @Before
    fun setUp() {
        verboseMessages.clear()
        Logger.instance().setLevel(SplitLogLevel.VERBOSE)
        Logger.instance().setPrinter(capturingPrinter)
    }

    @After
    fun tearDown() {
        Logger.instance().setLevel(SplitLogLevel.NONE)
    }

    @Test
    fun `fetchEvaluations logs request URL`() = runTest {
        val (client, _, _) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultRequest, -1L)

        assertTrue(
            "Expected URL in verbose logs, got: $verboseMessages",
            verboseMessages.any { it.contains(testEvaluationsUrl) }
        )
    }

    @Test
    fun `fetchEvaluations logs POST method`() = runTest {
        val (client, _, _) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultRequest, -1L)

        assertTrue(
            "Expected POST in verbose logs, got: $verboseMessages",
            verboseMessages.any { it.contains("POST") }
        )
    }

    @Test
    fun `fetchEvaluations logs response status`() = runTest {
        val http = FakeRetryableHttpClient(statusCode = 200)
        val (client, _, _) = makeClient(httpClient = http)

        client.fetchEvaluations(testDefaultTarget, testDefaultRequest, -1L)

        assertTrue(
            "Expected response status 200 in verbose logs, got: $verboseMessages",
            verboseMessages.any { it.contains("200") }
        )
    }

    @Test
    fun `fetchEvaluations logs request body`() = runTest {
        val (client, _, _) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultRequest, -1L)

        assertTrue(
            "Expected request body in verbose logs, got: $verboseMessages",
            verboseMessages.any { it.contains("user-1") }
        )
    }

    @Test
    fun `postEvents logs request URL and response status`() = runTest {
        val (client, _, _) = makeClient()

        client.postEvents("{}")

        assertTrue(
            "Expected events URL in verbose logs, got: $verboseMessages",
            verboseMessages.any { it.contains(testEventsUrl) }
        )
        assertTrue(
            "Expected response status in verbose logs, got: $verboseMessages",
            verboseMessages.any { it.contains("200") }
        )
    }

    @Test
    fun `postTelemetry logs request URL and response status`() = runTest {
        val (client, _, _) = makeClient()

        client.postTelemetry("{}")

        assertTrue(
            "Expected telemetry URL in verbose logs, got: $verboseMessages",
            verboseMessages.any { it.contains(testTelemetryUrl) }
        )
        assertTrue(
            "Expected response status in verbose logs, got: $verboseMessages",
            verboseMessages.any { it.contains("200") }
        )
    }

    @Test
    fun `fetchEvaluations logs both requests on 401 retry`() = runTest {
        val http = FakeRetryableHttpClient(statusCodeSequence = listOf(401, 200))
        val (client, _, _) = makeClient(httpClient = http)

        client.fetchEvaluations(testDefaultTarget, testDefaultRequest, -1L)

        val statusLogs = verboseMessages.filter { it.contains("401") || it.contains("200") }
        assertTrue(
            "Expected both 401 and 200 logged on retry, got: $verboseMessages",
            statusLogs.any { it.contains("401") } && statusLogs.any { it.contains("200") }
        )
    }

    @Test
    fun `request and response log lines share the same correlation ID`() = runTest {
        val (client, _, _) = makeClient()

        client.fetchEvaluations(testDefaultTarget, testDefaultRequest, -1L)

        val requestLine = verboseMessages.firstOrNull { it.contains(">>>") }
        val responseLine = verboseMessages.firstOrNull { it.contains("<<<") }
        assertTrue("Expected a request log line with >>>", requestLine != null)
        assertTrue("Expected a response log line with <<<", responseLine != null)

        val requestId = requestLine!!.substringAfter("[").substringBefore("]")
        val responseId = responseLine!!.substringAfter("[").substringBefore("]")
        assertTrue("Correlation ID must be non-empty", requestId.isNotEmpty())
        assertTrue(
            "Request and response must share the same correlation ID: '$requestId' vs '$responseId'",
            requestId == responseId
        )
    }

    @Test
    fun `retry uses the same correlation ID as the original request`() = runTest {
        val http = FakeRetryableHttpClient(statusCodeSequence = listOf(401, 200))
        val (client, _, _) = makeClient(httpClient = http)

        client.fetchEvaluations(testDefaultTarget, testDefaultRequest, -1L)

        val requestLines = verboseMessages.filter { it.contains(">>>") }
        assertTrue("Expected two request log lines for retry", requestLines.size == 2)
        val firstId = requestLines[0].substringAfter("[").substringBefore("]")
        val secondId = requestLines[1].substringAfter("[").substringBefore("]")
        assertTrue(
            "Both requests in a retry must share the same correlation ID: '$firstId' vs '$secondId'",
            firstId == secondId
        )
    }
}
