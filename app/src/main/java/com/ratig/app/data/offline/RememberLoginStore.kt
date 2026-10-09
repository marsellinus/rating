package com.ratig.app.data.offline

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Remembered sign-in preference (email only - the password is never stored). */
data class RememberedLogin(
    val remember: Boolean = false,
    val email: String = "",
    /** True once the first-run tutorial has been completed or skipped. */
    val onboardingDone: Boolean = false,
)

private val Context.rememberLoginDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "remember_login",
)

/**
 * Stores the "Ingat saya" preference (last email only) and the first-run
 * tutorial flag. The password is NEVER stored here - Supabase Auth keeps the
 * session token in its own encrypted storage.
 */
@Singleton
class RememberLoginStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val REMEMBER = booleanPreferencesKey("remember")
        val EMAIL = stringPreferencesKey("email")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    val state: Flow<RememberedLogin> = context.rememberLoginDataStore.data.map { prefs ->
        RememberedLogin(
            remember = prefs[Keys.REMEMBER] ?: false,
            email = prefs[Keys.EMAIL].orEmpty(),
            onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: false,
        )
    }

    suspend fun current(): RememberedLogin = state.first()

    /** Saves (or clears) the remembered email depending on [remember]. */
    suspend fun set(remember: Boolean, email: String) {
        context.rememberLoginDataStore.edit { prefs ->
            prefs[Keys.REMEMBER] = remember
            prefs[Keys.EMAIL] = if (remember) email.trim() else ""
        }
    }

    /** Marks the first-run tutorial as seen so it is not shown again. */
    suspend fun setOnboardingDone() {
        context.rememberLoginDataStore.edit { prefs -> prefs[Keys.ONBOARDING_DONE] = true }
    }
}
