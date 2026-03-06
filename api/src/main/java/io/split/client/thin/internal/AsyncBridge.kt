package io.split.client.thin.internal

import android.os.Handler
import android.os.Looper
import io.split.client.thin.SplitCallback
import io.split.client.thin.SplitVoidCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Seam used by [DefaultSplitFactory] — extracted so tests can inject a fake
 * without requiring Android's Handler/Looper.
 */
internal interface AsyncBridgeLike : AutoCloseable {
    fun <T> executeAsync(callback: SplitCallback<T>, block: suspend () -> T)
    fun executeAsync(callback: SplitVoidCallback, block: suspend () -> Unit)
}

/**
 * Shared async runtime intended to be owned by a SplitFactory-level component.
 * Implementations (client/manager/etc) delegate Java async bridges to this instance.
 */
internal class AsyncBridge(
    private val sdkScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) : AsyncBridgeLike {

    override fun <T> executeAsync(
        callback: SplitCallback<T>,
        block: suspend () -> T,
    ) {
        sdkScope.launch {
            try {
                val result = block()
                mainHandler.post { callback.onComplete(result, null) }
            } catch (t: Throwable) {
                mainHandler.post { callback.onComplete(null, t) }
            }
        }
    }

    override fun executeAsync(
        callback: SplitVoidCallback,
        block: suspend () -> Unit,
    ) {
        sdkScope.launch {
            try {
                block()
                mainHandler.post { callback.onComplete(null) }
            } catch (t: Throwable) {
                mainHandler.post { callback.onComplete(t) }
            }
        }
    }

    override fun close() {
        sdkScope.cancel()
    }
}
