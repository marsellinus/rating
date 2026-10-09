package com.ratig.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.rememberNavController
import com.ratig.app.core.i18n.Loc
import com.ratig.app.core.i18n.LocalAppLanguage
import com.ratig.app.core.i18n.LocalStrings
import com.ratig.app.data.offline.AppPreferences
import com.ratig.app.data.offline.AppPreferencesStore
import com.ratig.app.data.offline.ThemeChoice
import com.ratig.app.data.session.SessionViewModel
import com.ratig.app.ui.navigation.RatigNavHost
import com.ratig.app.ui.theme.RatigTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Exposes the persisted UI preferences (theme + text size) to the activity. */
@HiltViewModel
class UiPreferencesViewModel @Inject constructor(
    appPreferencesStore: AppPreferencesStore,
) : ViewModel() {
    val preferences: StateFlow<AppPreferences> = appPreferencesStore.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences())
}

/** Single-activity app. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefsVm: UiPreferencesViewModel = hiltViewModel()
            val prefs by prefsVm.preferences.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (prefs.theme) {
                ThemeChoice.SYSTEM -> systemDark
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK -> true
            }

            RatigTheme(
                darkTheme = darkTheme,
                textScale = prefs.textSize.scale,
            ) {
                val strings = remember(prefs.language) { Loc(prefs.language) }
                CompositionLocalProvider(
                    LocalAppLanguage provides prefs.language,
                    LocalStrings provides strings,
                ) {
                    val vm: SessionViewModel = hiltViewModel()
                    val session by vm.sessionState.collectAsState()
                    val navController = rememberNavController()
                    RatigNavHost(navController = navController, sessionState = session)
                }
            }
        }
    }
}
