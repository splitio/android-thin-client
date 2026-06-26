package io.split.client.thin.events

import io.split.android.client.tracker.TrackerValidationError
import io.split.android.client.utils.logger.LogPrinter
import io.split.android.client.utils.logger.Logger
import io.split.android.client.utils.logger.SplitLogLevel
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class TrackerLoggerImplTest {

    private lateinit var printer: LogPrinter
    private val trackerLogger = TrackerLoggerImpl()

    @Before
    fun setUp() {
        printer = mock(LogPrinter::class.java)
        Logger.instance().setLevel(SplitLogLevel.VERBOSE)
        Logger.instance().setPrinter(printer)
    }

    @Test
    fun `log with error delegates to Logger e`() {
        val error = TrackerValidationError(true, "bad event type")
        trackerLogger.log(error, "track")
        verify(printer).e(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.contains("bad event type"),
            ArgumentMatchers.isNull()
        )
    }

    @Test
    fun `log with warnings delegates to Logger w`() {
        val error = TrackerValidationError(listOf("traffic_type_name should be lowercase"))
        trackerLogger.log(error, "track")
        verify(printer).w(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.contains("lowercase"),
            ArgumentMatchers.isNull()
        )
    }

    @Test
    fun `e delegates to Logger e`() {
        trackerLogger.e("something went wrong", "track")
        verify(printer).e(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.contains("something went wrong"),
            ArgumentMatchers.isNull()
        )
    }

    @Test
    fun `v delegates to Logger v`() {
        trackerLogger.v("verbose message")
        verify(printer).v(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.contains("verbose message"),
            ArgumentMatchers.isNull()
        )
    }
}
