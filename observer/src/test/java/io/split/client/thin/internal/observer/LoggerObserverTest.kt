package io.split.client.thin.internal.observer

import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.contains
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class LoggerObserverTest {

    private lateinit var logger: Logger
    private lateinit var observer: LoggerObserver

    @Before
    fun setUp() {
        logger = mock(Logger::class.java)
        observer = LoggerObserver(logger)
    }

    @Test
    fun `logs INFO level events using Logger info`() {
        val event = ObservableEvent(
            type = "factory_init_started",
            properties = emptyMap()
        )

        observer.notifyEvent(event)

        verify(logger).info(contains("Init started"))
    }

    @Test
    fun `logs DEBUG level events using Logger debug`() {
        val event = ObservableEvent(
            type = "client_created",
            properties = emptyMap()
        )

        observer.notifyEvent(event)

        verify(logger).debug(contains("Client created"))
    }

    @Test
    fun `logs WARN level events using Logger warn`() {
        val event = ObservableEvent(
            type = "flush_failed",
            properties = emptyMap()
        )

        observer.notifyEvent(event)

        verify(logger).warn(contains("Flush failed"))
    }

    @Test
    fun `logs ERROR level events using Logger error`() {
        val event = ObservableEvent(
            type = "jwt_fetch_failed_non_retryable",
            properties = emptyMap()
        )

        observer.notifyEvent(event)

        verify(logger).error(contains("JWT fetch failed"))
    }

    @Test
    fun `logs streaming_notification_received at VERBOSE level`() {
        val event = ObservableEvent(
            type = "streaming_notification_received",
            properties = mapOf("notificationType" to "EVALUATIONS_UPDATE")
        )

        observer.notifyEvent(event)

        verify(logger).verbose(contains("Streaming notification received"))
    }

    @Test
    fun `logs unknown event types using DEBUG level`() {
        val event = ObservableEvent(
            type = "unknown_event",
            properties = emptyMap()
        )

        observer.notifyEvent(event)

        verify(logger).debug(contains("unknown_event"))
    }

    @Test
    fun `handles events with properties gracefully`() {
        val event = ObservableEvent(
            type = "factory_init_started",
            properties = mapOf("key" to "value")
        )

        observer.notifyEvent(event)

        verify(logger).info(contains("Init started"))
    }

    @Test
    fun `interpolates properties into log messages`() {
        val event = ObservableEvent(
            type = "http_request_started",
            properties = mapOf(
                "category" to "AUTH",
                "method" to "GET"
            )
        )

        observer.notifyEvent(event)

        verify(logger).debug(contains("HTTP AUTH GET started"))
    }

    @Test
    fun `interpolates JWT cache hit event`() {
        val event = ObservableEvent(
            type = "jwt_returned_from_storage",
            properties = mapOf("matchingKey" to "user-123")
        )

        observer.notifyEvent(event)

        verify(logger).debug(contains("JWT returned from cache"))
    }

    @Test
    fun `interpolates status code in HTTP retryable failure`() {
        val event = ObservableEvent(
            type = "http_request_failed_retryable",
            properties = mapOf(
                "category" to "AUTH",
                "statusCode" to "503"
            )
        )

        observer.notifyEvent(event)

        verify(logger).warn(contains("HTTP failed for AUTH (status: 503), retrying"))
    }

    @Test
    fun `interpolates status code in HTTP non-retryable failure`() {
        val event = ObservableEvent(
            type = "http_request_failed_non_retryable",
            properties = mapOf(
                "category" to "EVALUATIONS",
                "statusCode" to "401"
            )
        )

        observer.notifyEvent(event)

        verify(logger).error(contains("HTTP failed for EVALUATIONS (status: 401)"))
    }

    @Test
    fun `interpolates pushEnabled and connDelaySeconds in JWT fetch succeeded`() {
        val event = ObservableEvent(
            type = "jwt_fetch_succeeded",
            properties = mapOf(
                "pushEnabled" to "true",
                "connDelaySeconds" to "30"
            )
        )

        observer.notifyEvent(event)

        verify(logger).info(
            contains("JWT fetched (push enabled: true, connDelaySeconds: 30)")
        )
    }

    @Test
    fun `interpolates rate in poll trigger`() {
        val event = ObservableEvent(
            type = "poll_trigger",
            properties = mapOf("rate" to "3600s")
        )

        observer.notifyEvent(event)

        verify(logger).debug(contains("Polling (rate: 3600s)"))
    }

    @Test
    fun `interpolates reason in eval fetch requested`() {
        val event = ObservableEvent(
            type = "eval_fetch_requested",
            properties = mapOf("reason" to "PERIODIC")
        )

        observer.notifyEvent(event)

        verify(logger).info(contains("Evaluations fetch requested (reason: PERIODIC)"))
    }

    @Test
    fun `logs runtime sync fallback using Logger warn`() {
        val event = ObservableEvent(
            type = "runtime_sync_mode_changed",
            properties = mapOf(
                "from" to "STREAMING",
                "to" to "SINGLE_SYNC",
                "reason" to "AUTH_UNAUTHORIZED"
            )
        )

        observer.notifyEvent(event)

        verify(logger).warn(contains("Runtime sync mode changed from STREAMING to SINGLE_SYNC (reason: AUTH_UNAUTHORIZED)"))
    }
}
