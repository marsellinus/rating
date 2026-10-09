package com.ratig.app.data.offline

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.preferencesDataStoreFile
import com.ratig.app.core.time.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Offline/cache policy values resolved from [OfflinePolicyStore]. */
data class OfflinePolicy(
    val offlineValidityDays: Int,
    val cacheMaxAgeHours: Int,
    val nikMinLength: Int,
    val nikMaxLength: Int,
    val qrPrefix: String,
) {
    /** How long an offline login stays usable after the last server sync. */
    val offlineValidityMillis: Long get() = offlineValidityDays * MILLIS_PER_HOUR * 24

    /** How long a cached server row may be reused without a refresh. */
    val cacheMaxAgeMillis: Long get() = cacheMaxAgeHours * MILLIS_PER_HOUR

    /** True when a row cached at [cachedAtEpochMs] is still usable at [nowEpochMs]. */
    fun isCacheFresh(cachedAtEpochMs: Long, nowEpochMs: Long = TimeProvider.nowUtc().toEpochMilli()): Boolean =
        cachedAtEpochMs > 0 && nowEpochMs - cachedAtEpochMs <= cacheMaxAgeMillis

    companion object {
        const val DEFAULT_OFFLINE_VALIDITY_DAYS = 3
        const val DEFAULT_CACHE_MAX_AGE_HOURS = 72
        const val DEFAULT_NIK_MIN_LENGTH = 16
        const val DEFAULT_NIK_MAX_LENGTH = 16
        const val DEFAULT_QR_PREFIX = "RATIG1:"

        const val MILLIS_PER_HOUR = 60L * 60L * 1000L

        val DEFAULTS = OfflinePolicy(
            offlineValidityDays = DEFAULT_OFFLINE_VALIDITY_DAYS,
            cacheMaxAgeHours = DEFAULT_CACHE_MAX_AGE_HOURS,
            nikMinLength = DEFAULT_NIK_MIN_LENGTH,
            nikMaxLength = DEFAULT_NIK_MAX_LENGTH,
            qrPrefix = DEFAULT_QR_PREFIX,
        )
    }
}

private val Context.offlinePolicyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "offline_policy",
)

/**
 * Device policy store (DataStore preferences, internal storage). Holds only
 * non-sensitive operational policy values - never worker data or NIK.
 *
 * Defaults match CONTRACT-2 §Offline-first; the values exist so the
 * organization can tighten them later without an app release.
 */
@Singleton
class OfflinePolicyStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val OFFLINE_VALIDITY_DAYS = intPreferencesKey("offline_validity_days")
        val CACHE_MAX_AGE_HOURS = intPreferencesKey("cache_max_age_hours")
        val NIK_MIN_LENGTH = intPreferencesKey("nik_min_length")
        val NIK_MAX_LENGTH = intPreferencesKey("nik_max_length")
        val QR_PREFIX = stringPreferencesKey("qr_prefix")
    }

    /** Reactive policy stream; emits DEFAULTS (and corrections) until changed. */
    val policy: Flow<OfflinePolicy> = context.offlinePolicyDataStore.data.map { prefs ->
        OfflinePolicy(
            offlineValidityDays = prefs[Keys.OFFLINE_VALIDITY_DAYS]
                ?: OfflinePolicy.DEFAULT_OFFLINE_VALIDITY_DAYS,
            cacheMaxAgeHours = prefs[Keys.CACHE_MAX_AGE_HOURS]
                ?: OfflinePolicy.DEFAULT_CACHE_MAX_AGE_HOURS,
            nikMinLength = prefs[Keys.NIK_MIN_LENGTH] ?: OfflinePolicy.DEFAULT_NIK_MIN_LENGTH,
            nikMaxLength = prefs[Keys.NIK_MAX_LENGTH] ?: OfflinePolicy.DEFAULT_NIK_MAX_LENGTH,
            qrPrefix = prefs[Keys.QR_PREFIX] ?: OfflinePolicy.DEFAULT_QR_PREFIX,
        )
    }

    /** Individual reactive policies for UI layers. */
    val nikMinLength: Flow<Int> = policy.map { it.nikMinLength }
    val nikMaxLength: Flow<Int> = policy.map { it.nikMaxLength }
    val qrPrefix: Flow<String> = policy.map { it.qrPrefix }
    val offlineValidityDays: Flow<Int> = policy.map { it.offlineValidityDays }
    val cacheMaxAgeHours: Flow<Int> = policy.map { it.cacheMaxAgeHours }

    /** One-shot read for suspend call sites (repositories, the offline gate). */
    suspend fun current(): OfflinePolicy = policy.first()

    suspend fun offlineValidityMillis(): Long = current().offlineValidityMillis

    suspend fun cacheMaxAgeMillis(): Long = current().cacheMaxAgeMillis

    /** Cache freshness check against the stored [OfflinePolicy.cacheMaxAgeHours]. */
    suspend fun isCacheFresh(cachedAtEpochMs: Long, nowEpochMs: Long = TimeProvider.nowUtc().toEpochMilli()): Boolean =
        current().isCacheFresh(cachedAtEpochMs, nowEpochMs)
}
