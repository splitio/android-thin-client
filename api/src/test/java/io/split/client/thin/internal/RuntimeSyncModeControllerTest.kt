package io.split.client.thin.internal

import io.split.client.thin.internal.evaluation.PollingScheduler
import io.split.client.thin.internal.observer.ObservableEventType
import io.split.client.thin.internal.streaming.StreamingManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeSyncModeControllerTest {

    @Test
    fun `switchToSingleSync stops active polling and streaming`() = runTest {
        val polling = FakePollingScheduler()
        val streaming = FakeStreamingManager()
        val controller = RuntimeSyncModeController(this)
        controller.setPollingScheduler(polling)
        controller.setStreamingManager(streaming)

        controller.switchToSingleSync()
        advanceUntilIdle()

        assertTrue(controller.isSingleSync())
        assertEquals(1, polling.stopCalls)
        assertEquals(1, streaming.stopAllCalls)
    }

    @Test
    fun `switchToSingleSync is idempotent`() = runTest {
        val polling = FakePollingScheduler()
        val streaming = FakeStreamingManager()
        val controller = RuntimeSyncModeController(this)
        controller.setPollingScheduler(polling)
        controller.setStreamingManager(streaming)

        controller.switchToSingleSync()
        controller.switchToSingleSync()
        advanceUntilIdle()

        assertEquals(1, polling.stopCalls)
        assertEquals(1, streaming.stopAllCalls)
    }

    @Test
    fun `polling and streaming do not start or resume after single sync fallback`() = runTest {
        val polling = FakePollingScheduler()
        val streaming = FakeStreamingManager()
        var streamingStartCalls = 0
        val controller = RuntimeSyncModeController(this)

        controller.switchToSingleSync()
        controller.startPollingIfAllowed { polling }
        controller.resumePolling(polling)
        controller.startStreamingIfAllowed { streamingStartCalls++ }
        controller.resumeStreaming(streaming)
        advanceUntilIdle()

        assertEquals(0, polling.startCalls)
        assertEquals(0, polling.resumeCalls)
        assertEquals(0, streamingStartCalls)
        assertEquals(0, streaming.resumeCalls)
    }

    @Test
    fun `switchToSingleSync emits runtime sync mode changed event once`() = runTest {
        val capturedEvents = mutableListOf<Pair<String, Map<String, String>>>()
        val controller = RuntimeSyncModeController(
            scope = this,
            initialSyncMode = "STREAMING",
            onRuntimeSyncModeChanged = { type, properties -> capturedEvents.add(type to properties) },
        )

        controller.switchToSingleSync()
        controller.switchToSingleSync()

        assertEquals(1, capturedEvents.size)
        assertEquals(ObservableEventType.RUNTIME_SYNC_MODE_CHANGED, capturedEvents[0].first)
        assertEquals("STREAMING", capturedEvents[0].second["from"])
        assertEquals("SINGLE_SYNC", capturedEvents[0].second["to"])
        assertEquals("AUTH_UNAUTHORIZED", capturedEvents[0].second["reason"])
    }
}

private class FakePollingScheduler : PollingScheduler {
    var startCalls = 0
    var resumeCalls = 0
    var stopCalls = 0

    override fun start() {
        startCalls++
    }

    override fun pause() = Unit

    override fun resume() {
        resumeCalls++
    }

    override fun stop() {
        stopCalls++
    }
}

private class FakeStreamingManager : StreamingManager {
    var resumeCalls = 0
    var stopAllCalls = 0

    override suspend fun start() = Unit
    override suspend fun stop() = Unit
    override fun pause() = Unit

    override fun resume() {
        resumeCalls++
    }

    override suspend fun stopAll() {
        stopAllCalls++
    }
}
