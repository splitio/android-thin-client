package io.split.client.thin.internal.streaming

import io.split.client.thin.internal.streaming.PolicyEffect.CloseCurrentSocket
import io.split.client.thin.internal.streaming.PolicyEffect.EmitConnectStarted
import io.split.client.thin.internal.streaming.PolicyEffect.EmitConnected
import io.split.client.thin.internal.streaming.PolicyEffect.EmitDisconnected
import io.split.client.thin.internal.streaming.PolicyEffect.EmitSyncModeChanged
import io.split.client.thin.internal.streaming.PolicyEffect.Fetch
import io.split.client.thin.internal.streaming.PolicyEffect.InvalidateToken
import io.split.client.thin.internal.streaming.PolicyEffect.NotifyPushDisabled
import io.split.client.thin.internal.streaming.PolicyEffect.NotifyPushEnabled
import io.split.client.thin.internal.streaming.PolicyEffect.OpenSocket
import io.split.client.thin.internal.streaming.PolicyEffect.ResetBackoff
import io.split.client.thin.internal.streaming.PolicyEffect.ScheduleReconnect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure, synchronous tests for [StreamingPolicy.reduce]. No coroutines, no fakes — just
 * state-in / (state, effects)-out assertions covering each documented decision.
 */
class StreamingPolicyTest {

    private val started = PolicyState(connState = ConnState.Started)

    private fun reduce(state: PolicyState, event: PolicyEvent) = StreamingPolicy.reduce(state, event)

    // --- lifecycle -----------------------------------------------------------------------------

    @Test
    fun `start from stopped opens a socket`() {
        val (next, effects) = reduce(PolicyState(), PolicyEvent.Start)
        assertEquals(ConnState.Started, next.connState)
        assertEquals(listOf(EmitConnectStarted, OpenSocket), effects)
    }

    @Test
    fun `start while already started is idempotent`() {
        val (next, effects) = reduce(started, PolicyEvent.Start)
        assertEquals(started, next)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `stop closes socket and emits disconnected`() {
        val (next, effects) = reduce(started.copy(reconnecting = true), PolicyEvent.Stop)
        assertEquals(ConnState.Stopped, next.connState)
        assertFalse(next.reconnecting)
        assertEquals(listOf(CloseCurrentSocket, EmitDisconnected), effects)
    }

    @Test
    fun `pause from started closes socket without disconnect event`() {
        val (next, effects) = reduce(started.copy(reconnecting = true), PolicyEvent.Pause)
        assertEquals(ConnState.Paused, next.connState)
        assertFalse(next.reconnecting)
        assertEquals(listOf(CloseCurrentSocket), effects)
    }

    @Test
    fun `pause when not started does nothing`() {
        val (next, effects) = reduce(PolicyState(), PolicyEvent.Pause)
        assertEquals(PolicyState(), next)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `resume from paused reopens the socket`() {
        val (next, effects) = reduce(PolicyState(connState = ConnState.Paused), PolicyEvent.Resume)
        assertEquals(ConnState.Started, next.connState)
        assertEquals(listOf(EmitConnectStarted, OpenSocket), effects)
    }

    @Test
    fun `resume when not paused does nothing`() {
        assertTrue(reduce(started, PolicyEvent.Resume).second.isEmpty())
        assertTrue(reduce(PolicyState(), PolicyEvent.Resume).second.isEmpty())
    }

    // --- socket open / catch-up ----------------------------------------------------------------

    @Test
    fun `first healthy open resets backoff and fetches catch-up`() {
        val (next, effects) = reduce(started, PolicyEvent.SocketOpened)
        assertEquals(0, next.consecutiveFailures)
        assertFalse(next.reconnecting)
        assertEquals(listOf(EmitConnected, ResetBackoff, Fetch), effects)
    }

    @Test
    fun `open while control-paused does not fetch and stays push-down`() {
        val state = started.copy(controlPaused = true, pushUp = false)
        val (next, effects) = reduce(state, PolicyEvent.SocketOpened)
        assertFalse(next.pushUp)
        assertEquals(listOf(EmitConnected, ResetBackoff), effects)
    }

    @Test
    fun `recovery open clears connection-down and fires push-enabled, not an extra fetch`() {
        val state = started.copy(connectionDown = true, pushUp = false, consecutiveFailures = 2)
        val (next, effects) = reduce(state, PolicyEvent.SocketOpened)
        assertFalse(next.connectionDown)
        assertTrue(next.pushUp)
        assertEquals(
            listOf(EmitConnected, ResetBackoff, EmitSyncModeChanged("STREAMING", "CONNECTION_RECOVERED"), NotifyPushEnabled),
            effects,
        )
        assertFalse("recovery catch-up is owned by push-enabled, no extra Fetch", effects.contains(Fetch))
    }

    @Test
    fun `recovery open while control-paused neither re-enables push nor fetches`() {
        val state = started.copy(connectionDown = true, controlPaused = true, pushUp = false)
        val (next, effects) = reduce(state, PolicyEvent.SocketOpened)
        assertFalse(next.connectionDown)
        assertFalse(next.pushUp)
        assertEquals(listOf(EmitConnected, ResetBackoff), effects)
    }

    @Test
    fun `open re-enables push when occupancy had dropped to zero`() {
        val state = started.copy(occupancyZero = true, pushUp = false)
        val (next, effects) = reduce(state, PolicyEvent.SocketOpened)
        assertFalse(next.occupancyZero)
        assertTrue(next.pushUp)
        // occupancy edge fires push-enabled (no sync-mode event), then the explicit catch-up fetch.
        assertEquals(listOf(EmitConnected, ResetBackoff, NotifyPushEnabled, Fetch), effects)
    }

    // --- reconnect grace period + dedup --------------------------------------------------------

    @Test
    fun `first retryable error schedules reconnect without falling back to polling`() {
        val (next, effects) = reduce(started, PolicyEvent.SocketError(retryable = true))
        assertEquals(1, next.consecutiveFailures)
        assertTrue(next.reconnecting)
        assertFalse(next.connectionDown)
        assertEquals(listOf(ScheduleReconnect), effects)
    }

    @Test
    fun `second consecutive failure falls back to polling`() {
        // First failure owns the reconnect; the timer fires (clearing the dedup) and the retry
        // also fails — that second failure crosses the grace threshold.
        val (afterFirst, _) = reduce(started, PolicyEvent.SocketError(retryable = true))
        val (afterTimer, _) = reduce(afterFirst, PolicyEvent.ReconnectTimerFired)
        val (next, effects) = reduce(afterTimer, PolicyEvent.SocketError(retryable = true))
        assertEquals(2, next.consecutiveFailures)
        assertTrue(next.connectionDown)
        assertFalse(next.pushUp)
        assertEquals(
            listOf(EmitSyncModeChanged("POLLING_FALLBACK", "CONNECTION_RETRY"), NotifyPushDisabled, ScheduleReconnect),
            effects,
        )
    }

    @Test
    fun `retryable error while already reconnecting is dropped`() {
        val reconnecting = started.copy(reconnecting = true, consecutiveFailures = 1)
        val (next, effects) = reduce(reconnecting, PolicyEvent.SocketError(retryable = true))
        assertEquals(reconnecting, next)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `retryable error while not started is dropped`() {
        val (next, effects) = reduce(PolicyState(connState = ConnState.Paused), PolicyEvent.SocketError(retryable = true))
        assertEquals(ConnState.Paused, next.connState)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `second failure while control-paused crosses threshold without a redundant push-down callback`() {
        val paused = started.copy(controlPaused = true, pushUp = false, reconnecting = false, consecutiveFailures = 1)
        val (next, effects) = reduce(paused, PolicyEvent.SocketError(retryable = true))
        assertTrue(next.connectionDown)
        assertFalse(next.pushUp)
        // push was already down (control-paused) so no extra callback/sync event fires.
        assertEquals(listOf(ScheduleReconnect), effects)
    }

    @Test
    fun `reconnect timer fires open only while started`() {
        val (open, openEffects) = reduce(started.copy(reconnecting = true), PolicyEvent.ReconnectTimerFired)
        assertFalse(open.reconnecting)
        assertEquals(listOf(EmitConnectStarted, OpenSocket), openEffects)

        val (paused, pausedEffects) = reduce(
            PolicyState(connState = ConnState.Paused, reconnecting = true),
            PolicyEvent.ReconnectTimerFired,
        )
        assertFalse(paused.reconnecting)
        assertTrue(pausedEffects.isEmpty())
    }

    @Test
    fun `stale reconnect timer with dedup already cleared is a no-op`() {
        val (next, effects) = reduce(started.copy(reconnecting = false), PolicyEvent.ReconnectTimerFired)
        assertEquals(started, next)
        assertTrue(effects.isEmpty())
    }

    // --- non-retryable / token push disabled ---------------------------------------------------

    @Test
    fun `non-retryable error stops and falls back to polling`() {
        val (next, effects) = reduce(started, PolicyEvent.SocketError(retryable = false))
        assertEquals(ConnState.Stopped, next.connState)
        assertEquals(
            listOf(
                CloseCurrentSocket,
                EmitDisconnected,
                EmitSyncModeChanged("POLLING_FALLBACK", "CONNECTION_NON_RETRYABLE"),
                NotifyPushDisabled,
            ),
            effects,
        )
    }

    @Test
    fun `non-retryable error while not started is dropped`() {
        val (next, effects) = reduce(PolicyState(), PolicyEvent.SocketError(retryable = false))
        assertEquals(PolicyState(), next)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `token push disabled stops and notifies push disabled without a sync event`() {
        val (next, effects) = reduce(started, PolicyEvent.TokenPushDisabled)
        assertEquals(ConnState.Stopped, next.connState)
        assertEquals(listOf(CloseCurrentSocket, EmitDisconnected, NotifyPushDisabled), effects)
    }

    // --- error frames --------------------------------------------------------------------------

    @Test
    fun `token error frame invalidates, closes and reconnects`() {
        val (next, effects) = reduce(started, PolicyEvent.ErrorFrame(isTokenError = true))
        assertEquals(1, next.consecutiveFailures)
        assertTrue(next.reconnecting)
        assertEquals(listOf(InvalidateToken, CloseCurrentSocket, ScheduleReconnect), effects)
    }

    @Test
    fun `token error frame while already reconnecting still invalidates and closes but adds no reconnect`() {
        val reconnecting = started.copy(reconnecting = true)
        val (next, effects) = reduce(reconnecting, PolicyEvent.ErrorFrame(isTokenError = true))
        assertEquals(reconnecting, next)
        assertEquals(listOf(InvalidateToken, CloseCurrentSocket), effects)
    }

    @Test
    fun `non-token error frame produces no effects`() {
        val (next, effects) = reduce(started, PolicyEvent.ErrorFrame(isTokenError = false))
        assertEquals(started, next)
        assertTrue(effects.isEmpty())
    }

    // --- control: pause / resume / disable / reset ---------------------------------------------

    @Test
    fun `control pause brings push down`() {
        val (next, effects) = reduce(started, PolicyEvent.ControlPaused(eventTimestamp = 1000))
        assertTrue(next.controlPaused)
        assertEquals(1000L, next.lastControlTimestamp)
        assertFalse(next.pushUp)
        assertEquals(listOf(NotifyPushDisabled), effects)
    }

    @Test
    fun `control resume brings push back up over the same socket`() {
        val paused = started.copy(controlPaused = true, pushUp = false, lastControlTimestamp = 1000)
        val (next, effects) = reduce(paused, PolicyEvent.ControlResumed(eventTimestamp = 2000))
        assertFalse(next.controlPaused)
        assertTrue(next.pushUp)
        assertEquals(listOf(NotifyPushEnabled), effects)
    }

    @Test
    fun `stale control notification is ignored`() {
        val resumed = started.copy(lastControlTimestamp = 2000)
        val (next, effects) = reduce(resumed, PolicyEvent.ControlPaused(eventTimestamp = 1000))
        assertEquals(resumed, next)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `second pause while already paused does not re-fire push disabled`() {
        val paused = started.copy(controlPaused = true, pushUp = false, lastControlTimestamp = 1000)
        val (next, effects) = reduce(paused, PolicyEvent.ControlPaused(eventTimestamp = 2000))
        assertEquals(2000L, next.lastControlTimestamp)
        assertTrue(next.controlPaused)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `control disabled stops without reconnect`() {
        val (next, effects) = reduce(started, PolicyEvent.ControlDisabled)
        assertEquals(ConnState.Stopped, next.connState)
        assertFalse(next.reconnecting)
        assertTrue(effects.contains(CloseCurrentSocket))
        assertTrue(effects.contains(EmitDisconnected))
    }

    @Test
    fun `control disabled falls back to polling via NotifyPushDisabled`() {
        val (next, effects) = reduce(started, PolicyEvent.ControlDisabled)
        assertEquals(ConnState.Stopped, next.connState)
        assertFalse("must not be reconnecting after STREAMING_DISABLED", next.reconnecting)
        assertTrue(
            "must emit NotifyPushDisabled so polling fallback starts",
            effects.contains(NotifyPushDisabled),
        )
        assertTrue("must close current socket", effects.contains(CloseCurrentSocket))
        assertTrue("must emit disconnected", effects.contains(EmitDisconnected))
        val syncChange = effects.filterIsInstance<EmitSyncModeChanged>().singleOrNull()
        assertEquals("POLLING_FALLBACK", syncChange?.to)
        assertEquals("STREAMING_DISABLED", syncChange?.reason)
    }

    @Test
    fun `control reset closes and reopens preserving control-paused`() {
        val paused = started.copy(controlPaused = true, pushUp = false, lastControlTimestamp = 1000)
        val (next, effects) = reduce(paused, PolicyEvent.ControlReset)
        assertEquals(ConnState.Started, next.connState)
        assertTrue("control-paused survives a reset", next.controlPaused)
        assertEquals(listOf(CloseCurrentSocket, EmitDisconnected, EmitConnectStarted, OpenSocket), effects)
    }

    // --- occupancy ------------------------------------------------------------------------------

    @Test
    fun `occupancy zero brings push down`() {
        val (next, effects) = reduce(started, PolicyEvent.OccupancyChanged(isZero = true))
        assertTrue(next.occupancyZero)
        assertFalse(next.pushUp)
        assertEquals(listOf(NotifyPushDisabled), effects)
    }

    @Test
    fun `occupancy recovery brings push up when not control-paused`() {
        val zero = started.copy(occupancyZero = true, pushUp = false)
        val (next, effects) = reduce(zero, PolicyEvent.OccupancyChanged(isZero = false))
        assertTrue(next.pushUp)
        assertEquals(listOf(NotifyPushEnabled), effects)
    }

    @Test
    fun `occupancy recovery while control-paused stays push-down`() {
        val zeroPaused = started.copy(occupancyZero = true, controlPaused = true, pushUp = false)
        val (next, effects) = reduce(zeroPaused, PolicyEvent.OccupancyChanged(isZero = false))
        assertFalse(next.pushUp)
        assertTrue(effects.isEmpty())
    }
}
