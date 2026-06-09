package io.split.client.thin.internal.streaming

/**
 * Pure decision layer (FSM) for the streaming connection. It owns every *policy* decision —
 * lifecycle transitions, the 3-axis push gate (control / occupancy / connection), the reconnect
 * grace period and dedup, control pause/resume/disable/reset, occupancy edges and token-error
 * invalidation — and emits a list of [PolicyEffect]s describing what the runtime should do.
 *
 * It has no coroutine, Android or I/O dependency: [reduce] is a synchronous, side-effect-free
 * function. The runtime ([StreamingConnectionManager]) owns the *mechanism* (token fetch, socket
 * I/O, parsing, occupancy aggregation, timers) and executes the effects.
 */
internal enum class ConnState { Stopped, Started, Paused }

internal data class PolicyState(
    val connState: ConnState = ConnState.Stopped,
    val controlPaused: Boolean = false,
    val occupancyZero: Boolean = false,
    val connectionDown: Boolean = false,
    val consecutiveFailures: Int = 0,
    val reconnecting: Boolean = false,
    val lastControlTimestamp: Long = 0L,
    val pushUp: Boolean = true,
)

internal sealed class PolicyEvent {
    object Start : PolicyEvent()
    object Stop : PolicyEvent()
    object Pause : PolicyEvent()
    object Resume : PolicyEvent()
    object SocketOpened : PolicyEvent()
    data class SocketError(val retryable: Boolean) : PolicyEvent()
    object ReconnectTimerFired : PolicyEvent()
    object TokenPushDisabled : PolicyEvent()
    data class ErrorFrame(val isTokenError: Boolean) : PolicyEvent()
    data class ControlPaused(val eventTimestamp: Long) : PolicyEvent()
    data class ControlResumed(val eventTimestamp: Long) : PolicyEvent()
    object ControlDisabled : PolicyEvent()
    object ControlReset : PolicyEvent()
    data class OccupancyChanged(val isZero: Boolean) : PolicyEvent()
}

internal sealed class PolicyEffect {
    object OpenSocket : PolicyEffect()
    object CloseCurrentSocket : PolicyEffect()
    object ScheduleReconnect : PolicyEffect()
    object ResetBackoff : PolicyEffect()
    object InvalidateToken : PolicyEffect()
    object NotifyPushEnabled : PolicyEffect()
    object NotifyPushDisabled : PolicyEffect()
    object Fetch : PolicyEffect()
    object EmitConnectStarted : PolicyEffect()
    object EmitConnected : PolicyEffect()
    object EmitDisconnected : PolicyEffect()
    data class EmitSyncModeChanged(val to: String, val reason: String) : PolicyEffect()
}

internal object StreamingPolicy {

    fun reduce(state: PolicyState, event: PolicyEvent): Pair<PolicyState, List<PolicyEffect>> =
        when (event) {
            PolicyEvent.Start ->
                if (state.connState == ConnState.Started) state to emptyList()
                else state.copy(connState = ConnState.Started) to
                    listOf(PolicyEffect.EmitConnectStarted, PolicyEffect.OpenSocket)

            PolicyEvent.Stop ->
                state.copy(connState = ConnState.Stopped, reconnecting = false) to
                    listOf(PolicyEffect.CloseCurrentSocket, PolicyEffect.EmitDisconnected)

            PolicyEvent.Pause ->
                if (state.connState == ConnState.Started)
                    state.copy(connState = ConnState.Paused, reconnecting = false) to
                        listOf(PolicyEffect.CloseCurrentSocket)
                else state to emptyList()

            PolicyEvent.Resume ->
                if (state.connState == ConnState.Paused)
                    state.copy(connState = ConnState.Started) to
                        listOf(PolicyEffect.EmitConnectStarted, PolicyEffect.OpenSocket)
                else state to emptyList()

            PolicyEvent.SocketOpened -> onSocketOpened(state)

            is PolicyEvent.SocketError ->
                if (event.retryable) reconnect(state)
                else onNonRetryableError(state)

            PolicyEvent.ReconnectTimerFired ->
                if (!state.reconnecting) state to emptyList()
                else {
                    val s = state.copy(reconnecting = false)
                    if (s.connState == ConnState.Started)
                        s to listOf(PolicyEffect.EmitConnectStarted, PolicyEffect.OpenSocket)
                    else s to emptyList()
                }

            PolicyEvent.TokenPushDisabled ->
                state.copy(connState = ConnState.Stopped, reconnecting = false) to
                    listOf(
                        PolicyEffect.CloseCurrentSocket,
                        PolicyEffect.EmitDisconnected,
                        PolicyEffect.NotifyPushDisabled,
                    )

            is PolicyEvent.ErrorFrame -> {
                if (!event.isTokenError) state to emptyList()
                else {
                    val (next, reconnectEffects) = reconnect(state)
                    next to (listOf(PolicyEffect.InvalidateToken, PolicyEffect.CloseCurrentSocket) + reconnectEffects)
                }
            }

            is PolicyEvent.ControlPaused -> onControlPaused(state, paused = true, ts = event.eventTimestamp)
            is PolicyEvent.ControlResumed -> onControlPaused(state, paused = false, ts = event.eventTimestamp)

            PolicyEvent.ControlDisabled ->
                state.copy(connState = ConnState.Stopped, reconnecting = false) to
                    listOf(PolicyEffect.CloseCurrentSocket, PolicyEffect.EmitDisconnected)

            PolicyEvent.ControlReset ->
                state.copy(connState = ConnState.Started, reconnecting = false) to
                    listOf(
                        PolicyEffect.CloseCurrentSocket,
                        PolicyEffect.EmitDisconnected,
                        PolicyEffect.EmitConnectStarted,
                        PolicyEffect.OpenSocket,
                    )

            is PolicyEvent.OccupancyChanged -> {
                val (next, fx, _) = recomputePush(state.copy(occupancyZero = event.isZero), syncModeReason = null)
                next to fx
            }
        }

    /**
     * Successful socket open: clears the reconnect dedup flag, resets the failure counter and
     * occupancy (a fresh connection assumes publishers present), clears any connection-down
     * fallback (firing the push-up edge that owns the recovery catch-up) and otherwise requests an
     * explicit catch-up [PolicyEffect.Fetch] when push is up. [PolicyState.controlPaused] is NOT
     * reset (a server pause persists across reconnects).
     */
    private fun onSocketOpened(state: PolicyState): Pair<PolicyState, List<PolicyEffect>> {
        var s = state.copy(reconnecting = false)
        val effects = mutableListOf<PolicyEffect>(PolicyEffect.EmitConnected, PolicyEffect.ResetBackoff)

        val occ = recomputePush(s.copy(occupancyZero = false), syncModeReason = null)
        s = occ.first
        effects += occ.second

        s = s.copy(consecutiveFailures = 0)
        val needFetch: Boolean
        if (s.connectionDown) {
            val rec = recomputePush(s.copy(connectionDown = false), syncModeReason = "CONNECTION_RECOVERED")
            s = rec.first
            effects += rec.second
            // The recovery up-edge (onPushEnabled) owns the catch-up; only fetch explicitly when no
            // edge fired and push is up.
            needFetch = !rec.third && s.pushUp
        } else {
            needFetch = s.pushUp
        }
        if (needFetch) effects += PolicyEffect.Fetch
        return s to effects
    }

    /**
     * Retryable failure (socket error or token error frame). Deduped so concurrent triggers
     * coalesce into a single reconnect: a trigger while not [ConnState.Started] or already
     * [PolicyState.reconnecting] is dropped. The grace period only falls back to polling once a
     * retry has already failed (>= 2 consecutive failures).
     */
    private fun reconnect(state: PolicyState): Pair<PolicyState, List<PolicyEffect>> {
        if (state.connState != ConnState.Started || state.reconnecting) {
            return state to emptyList()
        }
        var s = state.copy(reconnecting = true, consecutiveFailures = state.consecutiveFailures + 1)
        val effects = mutableListOf<PolicyEffect>()
        if (s.consecutiveFailures >= 2 && !s.connectionDown) {
            val down = recomputePush(s.copy(connectionDown = true), syncModeReason = "CONNECTION_RETRY")
            s = down.first
            effects += down.second
        }
        effects += PolicyEffect.ScheduleReconnect
        return s to effects
    }

    private fun onNonRetryableError(state: PolicyState): Pair<PolicyState, List<PolicyEffect>> {
        if (state.connState != ConnState.Started) {
            return state to emptyList()
        }
        return state.copy(connState = ConnState.Stopped, reconnecting = false) to
            listOf(
                PolicyEffect.CloseCurrentSocket,
                PolicyEffect.EmitDisconnected,
                PolicyEffect.EmitSyncModeChanged("POLLING_FALLBACK", "CONNECTION_NON_RETRYABLE"),
                PolicyEffect.NotifyPushDisabled,
            )
    }

    private fun onControlPaused(state: PolicyState, paused: Boolean, ts: Long): Pair<PolicyState, List<PolicyEffect>> {
        // Ignore out-of-order control notifications (mirrors NotificationManagerKeeper).
        if (ts <= state.lastControlTimestamp) {
            return state to emptyList()
        }
        val (next, fx, _) = recomputePush(
            state.copy(lastControlTimestamp = ts, controlPaused = paused),
            syncModeReason = null,
        )
        return next to fx
    }

    /**
     * Recomputes the derived push axis (`pushUp = !controlPaused && !occupancyZero &&
     * !connectionDown`) on [next] and emits the edge callback only on a change. Connection-driven
     * transitions pass a [syncModeReason] (emitted as a sync-mode change); control/occupancy
     * transitions pass null (callback fires, no sync-mode event). Returns the updated state, the
     * effects, and whether the up-edge ([PolicyEffect.NotifyPushEnabled]) fired.
     */
    private fun recomputePush(next: PolicyState, syncModeReason: String?): Triple<PolicyState, List<PolicyEffect>, Boolean> {
        val newPushUp = !next.controlPaused && !next.occupancyZero && !next.connectionDown
        if (newPushUp == next.pushUp) {
            return Triple(next, emptyList(), false)
        }
        val updated = next.copy(pushUp = newPushUp)
        return if (newPushUp) {
            val effects = buildList {
                if (syncModeReason != null) add(PolicyEffect.EmitSyncModeChanged("STREAMING", syncModeReason))
                add(PolicyEffect.NotifyPushEnabled)
            }
            Triple(updated, effects, true)
        } else {
            val effects = buildList {
                if (syncModeReason != null) add(PolicyEffect.EmitSyncModeChanged("POLLING_FALLBACK", syncModeReason))
                add(PolicyEffect.NotifyPushDisabled)
            }
            Triple(updated, effects, false)
        }
    }
}
