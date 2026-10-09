package com.ratig.app.data.offline

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ratig.app.core.i18n.AppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** How the app follows the system light/dark setting. */
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/** Text size preset chosen during setup (a multiplier on the base scale). */
enum class TextSizeChoice(val scale: Float) {
    NORMAL(1.0f),
    LARGE(1.18f),
    EXTRA_LARGE(1.35f),
}

/** User interface preferences set during the first-run setup and editable later. */
data class AppPreferences(
    val setupDone: Boolean = false,
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val textSize: TextSizeChoice = TextSizeChoice.NORMAL,
    val language: AppLanguage = AppLanguage.ID,
)

private val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_preferences",
)

/**
 * Stores user interface preferences (theme + text size) chosen during the
 * first-run setup. Kept separate from auth data: it is UI-only, holds no PII.
 */
@Singleton
class AppPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val SETUP_DONE = booleanPreferencesKey("setup_done")
        val THEME = stringPreferencesKey("theme")
        val TEXT_SIZE = stringPreferencesKey("text_size")
        val LANGUAGE = stringPreferencesKey("language")
    }

    val preferences: Flow<AppPreferences> = context.appPreferencesDataStore.data.map { prefs ->
        AppPreferences(
            setupDone = prefs[Keys.SETUP_DONE] ?: false,
            theme = runCatching {
                ThemeChoice.valueOf(prefs[Keys.THEME] ?: ThemeChoice.SYSTEM.name)
            }.getOrDefault(ThemeChoice.SYSTEM),
            textSize = runCatching {
                TextSizeChoice.valueOf(prefs[Keys.TEXT_SIZE] ?: TextSizeChoice.NORMAL.name)
            }.getOrDefault(TextSizeChoice.NORMAL),
            language = AppLanguage.fromCode(prefs[Keys.LANGUAGE]),
        )
    }

    suspend fun current(): AppPreferences = preferences.first()

    suspend fun setSetupDone() {
        context.appPreferencesDataStore.edit { it[Keys.SETUP_DONE] = true }
    }

    suspend fun setTheme(theme: ThemeChoice) {
        context.appPreferencesDataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setTextSize(size: TextSizeChoice) {
        context.appPreferencesDataStore.edit { it[Keys.TEXT_SIZE] = size.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.appPreferencesDataStore.edit { it[Keys.LANGUAGE] = language.code }
    }
}
