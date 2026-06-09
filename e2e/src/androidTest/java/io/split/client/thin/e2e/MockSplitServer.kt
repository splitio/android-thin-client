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
 *         auth = server.url()
 *         evaluations = server.url()
 *         events = server.url()
 *         streaming = server.url()
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
    private val _capturedEventVersionHeaders = mutableListOf<String?>()
    private val _capturedTelemetryBodies = mutableListOf<String>()

    /** Number of requests received at the auth endpoint. */
    val authRequestCount: AtomicInteger = AtomicInteger(0)

    /** Last request received at the auth endpoint. */
    @Volatile var lastAuthRequest: RecordedRequest? = null
        private set

    /** All requests received at the auth endpoint, in order. */
    val capturedAuthRequests: MutableList<RecordedRequest> =
        Collections.synchronizedList(mutableListOf())

    /** Number of requests received at the evaluations endpoint. */
    val evaluationRequestCount: AtomicInteger = AtomicInteger(0)

    /** Last request received at the evaluations endpoint. */
    @Volatile var lastEvaluationsRequest: RecordedRequest? = null
        private set

    /** All requests received at the evaluations endpoint, in order. */
    val capturedEvaluationsRequests: MutableList<RecordedRequest> =
        Collections.synchronizedList(mutableListOf())

    /** Timestamps (ms) of each request received at the evaluations endpoint. */
    val evaluationRequestTimestampsMs: MutableList<Long> =
        Collections.synchronizedList(mutableListOf())

    /** Number of SSE connections established. */
    val sseConnectionCount: AtomicInteger = AtomicInteger(0)

    /** Bodies of all POST requests received at the events endpoint. */
    val capturedEventBodies: List<String> get() = _capturedEventBodies.toList()

    /** SplitSDKVersion headers of all POST requests received at the events endpoint. */
    val capturedEventVersionHeaders: List<String?> get() = _capturedEventVersionHeaders.toList()

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

                    path.startsWith("/api/evaluations") -> {
                        evaluationRequestCount.incrementAndGet()
                        evaluationRequestTimestampsMs.add(System.currentTimeMillis())
                        lastEvaluationsRequest = request
                        capturedEvaluationsRequests.add(request)
                        evaluationsHandler?.invoke(request)
                            ?: evaluationsQueue.removeFirstOrNull()
                            ?: MockResponse().setResponseCode(200)
                                .setBody("""{"till":-1,"since":-1,"evaluations":[]}""")
                    }

                    // Auth endpoint — must be checked after /api/v2 prefix to avoid false match
                    path.startsWith("/api") && request.method == "GET" -> {
                        authRequestCount.incrementAndGet()
                        lastAuthRequest = request
                        capturedAuthRequests.add(request)
                        authQueue.removeFirstOrNull() ?: MockResponse().setResponseCode(200)
                            .setBody(E2EFixtures.AUTH_PUSH_DISABLED)
                    }

                    path.startsWith("/api/events/bulk") -> {
                        _capturedEventVersionHeaders.add(request.getHeader("SplitSDKVersion"))
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

    /**
     * Builds a [MockResponse] that streams the given raw SSE frames verbatim.
     *
     * Unlike [buildSseResponse], each element of [rawFrames] is written exactly as given, so a
     * frame can include an `event:` line (e.g. `"event: error\ndata: {...}\n\n"`). Each frame
     * must be a complete SSE event terminated by a blank line (`\n\n`).
     *
     * [delaySeconds] positions the frame(s) so they arrive after the SDK has connected (achieved
     * with keepalive padding + throttling, so the socket is genuinely open in between).
     *
     * [keepOpenSeconds] keeps the connection open *after* the last frame with keepalive comments.
     * Set this when a test must prove recovery is driven by the frame's content rather than by the
     * server closing the stream (EOF) — with it the socket stays up, so any reconnect can only come
     * from the SDK acting on the frame.
     */
    fun buildRawSseResponse(
        rawFrames: List<String>,
        delaySeconds: Long = 0,
        keepOpenSeconds: Long = 0,
        bytesPerSecond: Int = 64,
    ): MockResponse {
        val buffer = Buffer()
        val keepAlive = ": keepalive\n\n"
        val keepAliveBytes = keepAlive.toByteArray(Charsets.UTF_8).size
        var bytesWritten = 0L

        fun padTo(targetBytes: Long) {
            while (bytesWritten < targetBytes) {
                buffer.writeUtf8(keepAlive)
                bytesWritten += keepAliveBytes
            }
        }

        // Position the frame at delaySeconds via keepalive padding (the socket is open meanwhile).
        padTo(delaySeconds * bytesPerSecond)
        for (frame in rawFrames) {
            buffer.writeUtf8(frame)
            bytesWritten += frame.toByteArray(Charsets.UTF_8).size
        }
        // Keep the socket open afterwards so recovery must be frame-driven, not EOF-driven.
        if (keepOpenSeconds > 0) {
            padTo(bytesWritten + keepOpenSeconds * bytesPerSecond)
        }

        return MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "text/event-stream")
            .addHeader("Cache-Control", "no-cache")
            .setBody(buffer)
            .throttleBody(bytesPerSecond.toLong(), 1, TimeUnit.SECONDS)
    }

    /**
     * Builds a single SSE [MockResponse] that emits multiple events spaced out in time over
     * one open connection. Each [timedEvents] entry is `(atSecond, dataLine)`: the event is
     * delivered roughly `atSecond` seconds after the connection opens. The connection is kept
     * open (with keepalive comments) for [trailingOpenSeconds] after the last event.
     *
     * Timing is approximated via body throttling: the stream emits [bytesPerSecond] bytes per
     * second, and keepalive padding is written so each event lands at its target byte offset.
     * This lets a test pause-then-resume (or push an update) over the *same* socket so
     * [sseConnectionCount] can assert no reconnect occurred.
     */
    fun buildTimedSseResponse(
        timedEvents: List<Pair<Long, String>>,
        bytesPerSecond: Int = 64,
        trailingOpenSeconds: Long = 180,
    ): MockResponse {
        val buffer = Buffer()
        val keepAlive = ": keepalive\n\n"
        val keepAliveBytes = keepAlive.toByteArray(Charsets.UTF_8).size
        var bytesWritten = 0L

        fun padTo(targetBytes: Long) {
            while (bytesWritten < targetBytes) {
                buffer.writeUtf8(keepAlive)
                bytesWritten += keepAliveBytes
            }
        }

        for ((atSecond, line) in timedEvents.sortedBy { it.first }) {
            padTo(atSecond * bytesPerSecond)
            val data = "data: $line\n\n"
            buffer.writeUtf8(data)
            bytesWritten += data.toByteArray(Charsets.UTF_8).size
        }
        padTo(bytesWritten + trailingOpenSeconds * bytesPerSecond)

        return MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "text/event-stream")
            .addHeader("Cache-Control", "no-cache")
            .setBody(buffer)
            .throttleBody(bytesPerSecond.toLong(), 1, TimeUnit.SECONDS)
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
        val buffer = Buffer()
        // Write enough keepalive comments to keep the connection open for a long time.
        // The space after ':' is critical: ": keepalive" does NOT match EventStreamParser's
        // KEEP_ALIVE_TOKEN (":keepalive"), so no onMessage events are dispatched.
        // Each pair is ~14 bytes; 10_000 pairs ≈ 140KB, lasts minutes with throttle.
        repeat(10_000) {
            buffer.writeUtf8(": keepalive\n\n")
        }
        return MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "text/event-stream")
            .addHeader("Cache-Control", "no-cache")
            .setBody(buffer)
            .throttleBody(14, 1, TimeUnit.SECONDS)  // ~1 comment pair per second
    }
}
