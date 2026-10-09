package com.ratig.app.data.offline

import com.ratig.app.core.time.TimeProvider
import com.ratig.app.data.local.dao.ProfileDao
import com.ratig.app.domain.model.AccountStatus
import com.ratig.app.domain.model.UserProfile
import com.ratig.app.domain.model.UserRole
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verdict of the offline gate: whether cached, server-verified identity data
 * may be used without a network connection, plus an Indonesian reason for
 * the decision (shown verbatim in the UI).
 */
data class OfflineDecision(
    val allowed: Boolean,
    val reason: String,
    /** Cached profile the decision was based on (NULL when nothing is cached). */
    val profile: UserProfile?,
    /** Role scope of the allowed session (profile role); NULL when denied. */
    val role: UserRole?,
    /** Epoch instant of the last confirmed server sync for the profile. */
    val lastSyncAt: Instant?,
    /** Epoch instant after which offline access expires (NULL when denied). */
    val validUntil: Instant?,
)

/**
 * Decides whether the app may operate offline from cached data.
 *
 * Rule (CONTRACT-2 §Offline-first): offline access is allowed iff a cached
 * profile exists AND its account status is ACTIVE AND the cache was written
 * within the offline validity window ([OfflinePolicyStore]). Only
 * server-verified rows are consulted: the gate reads Room exclusively, so a
 * manually typed identity can never produce an allowed decision.
 */
@Singleton
class OfflineGate @Inject constructor(
    private val profileDao: ProfileDao,
    private val policyStore: OfflinePolicyStore,
) {

    /**
     * @param userId auth.users id of the session to evaluate; when NULL the
     *   most recently cached profile is used (single-user-device assumption
     *   for the startup restore path).
     */
    suspend fun canAccessOffline(
        userId: String? = null,
        now: Instant = TimeProvider.nowUtc(),
    ): OfflineDecision {
        val cached = if (userId != null) {
            profileDao.byServerId(userId)
        } else {
            profileDao.latestCached()
        }
            ?: return OfflineDecision(
                allowed = false,
                reason = "Tidak ada sesi tersimpan di perangkat. Masuk memerlukan koneksi internet.",
                profile = null,
                role = null,
                lastSyncAt = null,
                validUntil = null,
            )

        val profile = cached.toDomain()
        if (AccountStatus.fromRaw(cached.accountStatus) != AccountStatus.ACTIVE) {
            return OfflineDecision(
                allowed = false,
                reason = "Akun Anda belum aktif (menunggu persetujuan atau dinonaktifkan). " +
                    "Akses luring tidak diizinkan.",
                profile = profile,
                role = profile.role,
                lastSyncAt = null,
                validUntil = null,
            )
        }

        val policy = policyStore.current()
        val lastSync = Instant.ofEpochMilli(cached.cachedAt)
        val validUntil = lastSync.plusMillis(policy.offlineValidityMillis)
        if (now.isAfter(validUntil)) {
            return OfflineDecision(
                allowed = false,
                reason = "Sesi luring telah kedaluwarsa (batas ${policy.offlineValidityDays} hari " +
                    "sejak sinkronisasi terakhir). Sambungkan internet untuk memperbarui.",
                profile = profile,
                role = profile.role,
                lastSyncAt = lastSync,
                validUntil = validUntil,
            )
        }

        return OfflineDecision(
            allowed = true,
            reason = "Mode luring: memakai data tersimpan di perangkat " +
                "(berlaku sampai ${TimeProvider.formatDateForDisplay(validUntil)}).",
            profile = profile,
            role = profile.role,
            lastSyncAt = lastSync,
            validUntil = validUntil,
        )
    }
}
