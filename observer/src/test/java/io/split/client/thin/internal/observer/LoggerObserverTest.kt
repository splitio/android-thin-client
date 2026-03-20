package io.split.client.thin.internal.observer

import io.split.android.client.utils.logger.LogPrinter
import io.split.android.client.utils.logger.Logger
import io.split.android.client.utils.logger.SplitLogLevel
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class LoggerObserverTest {

    private lateinit var printer: LogPrinter
    private lateinit var observer: LoggerObserver

    @Before
    fun setUp() {
        printer = mock(LogPrinter::class.java)
        Logger.instance().setLevel(SplitLogLevel.VERBOSE)
        Logger.instance().setPrinter(printer)
        observer = LoggerObserver()
    }

    @Test
    fun `logs INFO level events using Logger info`() {
        val event = ObservableEvent(
            type = "factory_init_started",
            properties = emptyMap()
        )

        observer.notifyEvent(event)

        verify(printer).i(anyString(), contains("Init started"), isNull())
    }

    @Test
    fun `logs DEBUG level events using Logger debug`() {
        val event = ObservableEvent(
            type = "client_created",
            properties = emptyMap()
        )

        observer.notifyEvent(event)

        verify(printer).d(anyString(), contains("Client created"), isNull())
    }

    @Test
    fun `logs WARN level events using Logger warn`() {
        val event = ObservableEvent(
            type = "flush_failed",
            properties = emptyMap()
        )

        observer.notifyEvent(event)

        verify(printer).w(anyString(), contains("Flush failed"), isNull())
    }

    @Test
    fun `logs ERROR level events using Logger error`() {
        val event = ObservableEvent(
            type = "jwt_fetch_failed_non_retryable",
            properties = emptyMap()
        )

        observer.notifyEvent(event)

        verify(printer).e(anyString(), contains("JWT fetch failed"), isNull())
    }

    @Test
    fun `logs unknown event types using DEBUG level`() {
        val event = ObservableEvent(
            type = "unknown_event",
            properties = emptyMap()
        )

        observer.notifyEvent(event)

        verify(printer).d(anyString(), contains("unknown_event"), isNull())
    }

    @Test
    fun `handles events with properties gracefully`() {
        val event = ObservableEvent(
            type = "factory_init_started",
            properties = mapOf("key" to "value")
        )

        observer.notifyEvent(event)

        verify(printer).i(anyString(), contains("Init started"), isNull())
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

        verify(printer).d(anyString(), contains("HTTP AUTH GET started"), isNull())
    }

    @Test
    fun `interpolates JWT cache hit event`() {
        val event = ObservableEvent(
            type = "jwt_returned_from_storage",
            properties = mapOf("matchingKey" to "user-123")
        )

        observer.notifyEvent(event)

        verify(printer).d(anyString(), contains("JWT returned from cache"), isNull())
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

        verify(printer).w(anyString(), contains("HTTP failed for AUTH (status: 503), retrying"), isNull())
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

        verify(printer).e(anyString(), contains("HTTP failed for EVALUATIONS (status: 401)"), isNull())
    }

    @Test
    fun `interpolates pushEnabled in JWT fetch succeeded`() {
        val event = ObservableEvent(
            type = "jwt_fetch_succeeded",
            properties = mapOf("pushEnabled" to "true")
        )

        observer.notifyEvent(event)

        verify(printer).i(anyString(), contains("JWT fetched (push enabled: true)"), isNull())
    }

    @Test
    fun `interpolates rate in poll trigger`() {
        val event = ObservableEvent(
            type = "poll_trigger",
            properties = mapOf("rate" to "3600s")
        )

        observer.notifyEvent(event)

        verify(printer).d(anyString(), contains("Polling (rate: 3600s)"), isNull())
    }

    @Test
    fun `interpolates reason in eval fetch requested`() {
        val event = ObservableEvent(
            type = "eval_fetch_requested",
            properties = mapOf("reason" to "PERIODIC")
        )

        observer.notifyEvent(event)

        verify(printer).i(anyString(), contains("Evaluations fetch requested (reason: PERIODIC)"), isNull())
    }
}
