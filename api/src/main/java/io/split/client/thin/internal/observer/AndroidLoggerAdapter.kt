package io.split.client.thin.internal.observer

import io.split.android.client.utils.logger.Logger as AndroidLogger

/**
 * Adapter that implements observer.Logger by delegating to the android-client Logger singleton.
 */
internal class AndroidLoggerAdapter : Logger {
    override fun debug(message: String) {
        AndroidLogger.d(message)
    }

    override fun info(message: String) {
        AndroidLogger.i(message)
    }

    override fun warn(message: String) {
        AndroidLogger.w(message)
    }

    override fun error(message: String) {
        AndroidLogger.e(message)
    }
}
