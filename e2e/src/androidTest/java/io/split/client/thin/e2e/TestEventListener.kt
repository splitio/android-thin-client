package io.split.client.thin.e2e

import io.split.client.thin.SdkReadyMetadata
import io.split.client.thin.SdkUpdateMetadata
import io.split.client.thin.SplitClient
import io.split.client.thin.SplitEventListener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal const val READY_TIMEOUT_SECONDS = 15L
internal const val UPDATE_TIMEOUT_SECONDS = 15L
internal const val NO_FIRE_TIMEOUT_SECONDS = 3L

/**
 * Test helper that wraps [SplitEventListener] with [CountDownLatch]-based awaits for each
 * event type, removing boilerplate from test methods.
 *
 * Usage:
 * ```kotlin
 * val listener = TestEventListener()
 * client.addEventListener(listener.asSplitEventListener)
 *
 * assertTrue("onReady did not fire", listener.awaitReady())
 * assertEquals("on", client.getTreatment("flag_a").treatment)
 * ```
 */
class TestEventListener {

    private val readyLatch = CountDownLatch(1)
    private val cacheReadyLatch = CountDownLatch(1)
    private val updateLatch = CountDownLatch(1)
    private val timeoutLatch = CountDownLatch(1)

    var lastReadyMetadata: SdkReadyMetadata? = null
        private set
    var lastUpdateMetadata: SdkUpdateMetadata? = null
        private set

    /** True once [SplitEventListener.onReady] has fired. */
    val isReadyFired: Boolean get() = readyLatch.count == 0L

    val asSplitEventListener: SplitEventListener = object : SplitEventListener() {
        override fun onReady(client: SplitClient, metadata: SdkReadyMetadata?) {
            lastReadyMetadata = metadata
            readyLatch.countDown()
        }

        override fun onReadyFromCache(client: SplitClient, metadata: SdkReadyMetadata?) {
            cacheReadyLatch.countDown()
        }

        override fun onUpdate(client: SplitClient, metadata: SdkUpdateMetadata?) {
            lastUpdateMetadata = metadata
            updateLatch.countDown()
        }

        override fun onTimeout(client: SplitClient) {
            timeoutLatch.countDown()
        }
    }

    /** Blocks until [SplitEventListener.onReady] fires or [timeoutSeconds] elapses. */
    fun awaitReady(timeoutSeconds: Long = READY_TIMEOUT_SECONDS): Boolean =
        readyLatch.await(timeoutSeconds, TimeUnit.SECONDS)

    /** Blocks until [SplitEventListener.onReadyFromCache] fires or [timeoutSeconds] elapses. */
    fun awaitCacheReady(timeoutSeconds: Long = READY_TIMEOUT_SECONDS): Boolean =
        cacheReadyLatch.await(timeoutSeconds, TimeUnit.SECONDS)

    /** Blocks until [SplitEventListener.onUpdate] fires or [timeoutSeconds] elapses. */
    fun awaitUpdate(timeoutSeconds: Long = UPDATE_TIMEOUT_SECONDS): Boolean =
        updateLatch.await(timeoutSeconds, TimeUnit.SECONDS)

    /** Blocks until [SplitEventListener.onTimeout] fires or [timeoutSeconds] elapses. */
    fun awaitTimeout(timeoutSeconds: Long = READY_TIMEOUT_SECONDS): Boolean =
        timeoutLatch.await(timeoutSeconds, TimeUnit.SECONDS)

    /**
     * Returns `true` if [SplitEventListener.onUpdate] did NOT fire within [waitSeconds].
     *
     * Use for negative assertions: `assertTrue("no spurious update", listener.noUpdate())`.
     */
    fun noUpdate(waitSeconds: Long = NO_FIRE_TIMEOUT_SECONDS): Boolean =
        !updateLatch.await(waitSeconds, TimeUnit.SECONDS)
}
