package com.ratig.app.data.offline

import com.ratig.app.domain.model.UserProfile
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Offline identity restored from the Room cache (server-verified data only).
 *
 * `restricted = true` means the cached identity itself is expired or no
 * longer ACTIVE ([OfflineDecision.allowed] == false): the UI must switch to
 * a view-only mode over cached results - no new examinations, no admin
 * flows, nothing that would present the identity as currently valid.
 */
data class RestoredOfflineSession(
    val profile: UserProfile,
    val decision: OfflineDecision,
    val restricted: Boolean,
    /** Epoch instant after which this restored session expires; NULL if restricted. */
    val validUntil: Instant?,
)

/**
 * Restores the last known ACTIVE profile from the Room cache when the device
 * has no network, so an examiner can keep working after a relaunch offline.
 *
 * Security invariants:
 *  - Only rows previously confirmed by the server are ever restored - there
 *    is no API that turns a manually typed identity into a session.
 *  - Google sign-in and any new authentication ALWAYS require the network;
 *    this class never touches [com.ratig.app.core.di]'s SupabaseClient and
 *    never fabricates or mutates auth state.
 *  - A restricted restoration carries the denial reason verbatim for the UI.
 */
@Singleton
class OfflineSessionManager @Inject constructor(
    private val offlineGate: OfflineGate,
    private val networkMonitor: NetworkMonitor,
) {

    private val _offlineSession = MutableStateFlow<RestoredOfflineSession?>(null)

    /** Last restoration performed this process; NULL when online-only. */
    val offlineSession: StateFlow<RestoredOfflineSession?> = _offlineSession.asStateFlow()

    /**
     * Restores the cached session when offline. Returns NULL when online
     * (the normal authenticated flow owns the session) or when nothing
     * usable is cached. Publishes the result to [offlineSession].
     *
     * @param userId auth.users id of the Supabase session restored from
     *   local storage (optional; when absent the most recently cached
     *   profile on the device is used).
     */
    suspend fun restoreIfOffline(userId: String? = null): RestoredOfflineSession? {
        if (networkMonitor.isOnline.value) return null
        val decision = offlineGate.canAccessOffline(userId)
        val profile = decision.profile ?: return null
        val restored = RestoredOfflineSession(
            profile = profile,
            decision = decision,
            restricted = !decision.allowed,
            validUntil = decision.validUntil,
        )
        _offlineSession.value = restored
        return restored
    }

    /** Drops the restored session (sign-out, or connectivity returned). */
    fun clear() {
        _offlineSession.value = null
    }
}
