package io.split.client.thin.e2e

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Wraps [MockWebServer] and dispatches requests by path to simulate the Split backend.
 *
 * Each response queue is consumed in FIFO order. When a queue is empty the server
 * returns HTTP 200 with an empty body, so tests must enqueue responses before
 * creating the SDK factory.
 *
 * Usage:
 * ```kotlin
 * val server = MockSplitServer()
 * server.enqueueAuth(MockResponse().setBody(E2EFixtures.AUTH_PUSH_DISABLED))
 * server.enqueueEvaluations(MockResponse().setBody(E2EFixtures.EVALUATIONS_RESPONSE_1))
 *
 * val config = splitClientConfig {
 *     serviceEndpoints {
 *         authUrl = server.url("/api")
 *         evaluationsUrl = server.url("/api/v2/evaluations")
 *         eventsUrl = server.url("/api/v1/events/bulk")
 *         telemetryUrl = server.url("/api/v1/metrics/config")
 *         streamingUrl = server.url("/sse")
 *     }
 * }
 * // ... use the SDK ...
 * server.shutdown()
 * ```
 */
class MockSplitServer {

    private val server = MockWebServer()
    private val authQueue = ArrayDeque<MockResponse>()
    private val evaluationsQueue = ArrayDeque<MockResponse>()
    private val sseQueue = ArrayDeque<MockResponse>()

    private val _capturedEventBodies = mutableListOf<String>()
    private val _capturedTelemetryBodies = mutableListOf<String>()

    /** Number of requests received at the auth endpoint. */
    val authRequestCount: AtomicInteger = AtomicInteger(0)

    /** Number of requests received at the evaluations endpoint. */
    val evaluationRequestCount: AtomicInteger = AtomicInteger(0)

    /** Timestamps (ms) of each request received at the evaluations endpoint. */
    val evaluationRequestTimestampsMs: MutableList<Long> =
        Collections.synchronizedList(mutableListOf())

    /** Number of SSE connections established. */
    val sseConnectionCount: AtomicInteger = AtomicInteger(0)

    /** Bodies of all POST requests received at the events endpoint. */
    val capturedEventBodies: List<String> get() = _capturedEventBodies.toList()

    /** Bodies of all POST requests received at the telemetry endpoint. */
    val capturedTelemetryBodies: List<String> get() = _capturedTelemetryBodies.toList()

    /**
     * Optional per-request handler for the evaluations endpoint. When set, takes precedence
     * over [evaluationsQueue]. Useful for dispatch-by-query-param logic in multi-client tests.
     *
     * The handler receives the full [RecordedRequest] and returns a [MockResponse] or `null`
     * to fall through to [evaluationsQueue].
     */
    @Volatile var evaluationsHandler: ((RecordedRequest) -> MockResponse?)? = null

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/sse") -> {
                        sseConnectionCount.incrementAndGet()
                        sseQueue.removeFirstOrNull() ?: defaultSseResponse()
                    }

                    path.startsWith("/api/v2/evaluations") -> {
                        evaluationRequestCount.incrementAndGet()
                        evaluationRequestTimestampsMs.add(System.currentTimeMillis())
                        evaluationsHandler?.invoke(request)
                            ?: evaluationsQueue.removeFirstOrNull()
                            ?: MockResponse().setResponseCode(200)
                                .setBody("""{"till":-1,"since":-1,"evaluations":[]}""")
                    }

                    // Auth endpoint — must be checked after /api/v2 prefix to avoid false match
                    path.startsWith("/api") && request.method == "GET" -> {
                        authRequestCount.incrementAndGet()
                        authQueue.removeFirstOrNull() ?: MockResponse().setResponseCode(200)
                            .setBody(E2EFixtures.AUTH_PUSH_DISABLED)
                    }

                    path.startsWith("/api/v1/events/bulk") -> {
                        _capturedEventBodies.add(request.body.readUtf8())
                        MockResponse().setResponseCode(200)
                    }

                    path.startsWith("/api/v1/metrics/config") -> {
                        _capturedTelemetryBodies.add(request.body.readUtf8())
                        MockResponse().setResponseCode(200)
                    }

                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    /** Base URL for the mock server (e.g. `"http://localhost:PORT"`). */
    fun url(path: String = "/"): String = server.url(path).toString()

    // -------------------------------------------------------------------------
    // Enqueue helpers
    // -------------------------------------------------------------------------

    fun enqueueAuth(response: MockResponse) {
        authQueue.addLast(response)
    }

    fun enqueueEvaluations(response: MockResponse) {
        evaluationsQueue.addLast(response)
    }

    /**
     * Enqueues a streaming SSE response.
     *
     * Use [buildSseResponse] to construct a valid SSE [MockResponse] from raw
     * `data:` lines.
     */
    fun enqueueSse(response: MockResponse) {
        sseQueue.addLast(response)
    }

    // -------------------------------------------------------------------------
    // SSE helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a [MockResponse] that streams the given SSE data lines.
     *
     * Each element of [dataLines] is written as `data: <line>\n\n`.
     *
     * [delaySeconds] adds a body delay so the SSE event arrives after the SDK
     * has already reached a ready state — useful when the test needs onReady to
     * fire before the update notification.
     *
     * Example:
     * ```kotlin
     * server.enqueueSse(
     *     server.buildSseResponse(listOf(E2EFixtures.SSE_EVALUATION_UPDATE), delaySeconds = 2)
     * )
     * ```
     */
    fun buildSseResponse(dataLines: List<String>, delaySeconds: Long = 0): MockResponse {
        val buffer = Buffer()
        for (line in dataLines) {
            buffer.writeUtf8("data: $line\n\n")
        }
        return MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "text/event-stream")
            .addHeader("Cache-Control", "no-cache")
            .setBodyDelay(delaySeconds, TimeUnit.SECONDS)
            .setBody(buffer)
            .throttleBody(Long.MAX_VALUE, 1, TimeUnit.SECONDS)
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    fun shutdown() {
        try {
            server.shutdown()
        } catch (_: java.io.IOException) {
            // MockWebServer may throw if connections with delayed responses are still open
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Default SSE response when the queue is empty: keeps the connection open
     * indefinitely with no events, simulating a quiet streaming connection.
     */
    private fun defaultSseResponse(): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "text/event-stream")
            .addHeader("Cache-Control", "no-cache")
            .setBody(Buffer())
            .throttleBody(Long.MAX_VALUE, 1, TimeUnit.SECONDS)
    }
}
