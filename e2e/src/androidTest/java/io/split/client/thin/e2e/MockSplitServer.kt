package io.split.client.thin.e2e

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import java.util.concurrent.TimeUnit

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

    /** Bodies of all POST requests received at the events endpoint. */
    val capturedEventBodies: List<String> get() = _capturedEventBodies.toList()

    /** Bodies of all POST requests received at the telemetry endpoint. */
    val capturedTelemetryBodies: List<String> get() = _capturedTelemetryBodies.toList()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/sse") ->
                        sseQueue.removeFirstOrNull() ?: defaultSseResponse()

                    path.startsWith("/api/v2/evaluations") ->
                        evaluationsQueue.removeFirstOrNull() ?: MockResponse().setResponseCode(200)
                            .setBody("""{"till":-1,"since":-1,"evaluations":[]}""")

                    // Auth endpoint — must be checked after /api/v2 prefix to avoid false match
                    path.startsWith("/api") && request.method == "GET" ->
                        authQueue.removeFirstOrNull() ?: MockResponse().setResponseCode(200)
                            .setBody(E2EFixtures.AUTH_PUSH_DISABLED)

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
     * Builds a [MockResponse] that streams the given SSE data lines followed by
     * a keep-alive comment, then closes the connection.
     *
     * Each element of [dataLines] is written as `data: <line>\n\n`.
     *
     * Example:
     * ```kotlin
     * server.enqueueSse(
     *     MockSplitServer.buildSseResponse(E2EFixtures.SSE_EVALUATION_UPDATE)
     * )
     * ```
     */
    fun buildSseResponse(vararg dataLines: String): MockResponse {
        val buffer = Buffer()
        for (line in dataLines) {
            buffer.writeUtf8("data: $line\n\n")
        }
        return MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "text/event-stream")
            .addHeader("Cache-Control", "no-cache")
            .setBody(buffer)
            .throttleBody(Long.MAX_VALUE, 1, TimeUnit.SECONDS)
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    fun shutdown() {
        server.shutdown()
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
