package com.ratig.app.core.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for sync state shown in the UI. Updated by three
 * independent producers:
 *  - [com.ratig.app.data.offline.NetworkMonitor] collector (isOnline),
 *  - Room count flows (pendingSessions / pendingTrials / failedCount),
 *  - [SyncWorker] (lastSyncAt / lastSyncError).
 * All mutations are atomic; the bus never logs and never stores NIK/tokens.
 */
@Singleton
class SyncStatusBus @Inject constructor() {

    private val _snapshot = MutableStateFlow(SyncSnapshot())
    val snapshot: StateFlow<SyncSnapshot> = _snapshot.asStateFlow()

    fun setOnline(value: Boolean) = _snapshot.update { it.copy(isOnline = value) }

    fun setCounts(pendingSessions: Int, pendingTrials: Int, failedCount: Int) =
        _snapshot.update {
            it.copy(
                pendingSessions = pendingSessions.coerceAtLeast(0),
                pendingTrials = pendingTrials.coerceAtLeast(0),
                failedCount = failedCount.coerceAtLeast(0),
            )
        }

    /** Called only after the server CONFIRMED a session (finalize 200). */
    fun onSessionSynced(at: Instant = Instant.now()) =
        _snapshot.update { it.copy(lastSyncAt = at, lastSyncError = null) }

    /** Records the latest sanitized failure reason; never a raw exception. */
    fun onSyncFailed(reason: String?) =
        _snapshot.update { it.copy(lastSyncError = reason?.take(REASON_MAX_LENGTH)) }

    private companion object {
        const val REASON_MAX_LENGTH = 200
    }
}
