package io.split.client.thin.events

import io.split.android.client.tracker.TrackerLogger
import io.split.android.client.tracker.TrackerValidationError
import io.split.android.client.utils.logger.Logger

internal class TrackerLoggerImpl : TrackerLogger {

    override fun log(errorInfo: TrackerValidationError, tag: String) {
        if (errorInfo.isError) {
            Logger.e("[${tag}] ${errorInfo.message}")
        } else {
            errorInfo.warnings.forEach { warning ->
                Logger.w("[${tag}] $warning")
            }
        }
    }

    override fun e(message: String, tag: String) {
        Logger.e("[${tag}] $message")
    }

    override fun v(message: String) {
        Logger.v(message)
    }
}
