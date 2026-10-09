package com.ratig.app.feature.setup

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ratig.app.core.i18n.AppLanguage
import com.ratig.app.core.i18n.Loc
import com.ratig.app.core.i18n.LocalStrings
import com.ratig.app.core.i18n.S
import com.ratig.app.data.offline.AppPreferences
import com.ratig.app.data.offline.AppPreferencesStore
import com.ratig.app.data.offline.TextSizeChoice
import com.ratig.app.data.offline.ThemeChoice
import com.ratig.app.ui.theme.RatigTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val appPreferencesStore: AppPreferencesStore,
) : ViewModel() {

    /** Current saved preferences, loaded so re-opening the screen shows them. */
    private val _current = MutableStateFlow<AppPreferences?>(null)
    val current: StateFlow<AppPreferences?> = _current.asStateFlow()

    init {
        viewModelScope.launch { _current.value = appPreferencesStore.current() }
    }

    /** Persists the chosen preferences and signals completion. */
    fun finish(
        theme: ThemeChoice,
        textSize: TextSizeChoice,
        language: AppLanguage,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            appPreferencesStore.setTheme(theme)
            appPreferencesStore.setTextSize(textSize)
            appPreferencesStore.setLanguage(language)
            appPreferencesStore.setSetupDone()
            onDone()
        }
    }
}

/** Plain-language setup shown once before the first sign-in. */
@Composable
fun SetupRoute(
    onDone: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val systemDark = isSystemInDarkTheme()
    val current by viewModel.current.collectAsStateWithLifecycle()
    var theme by remember { mutableStateOf(ThemeChoice.SYSTEM) }
    var textSize by remember { mutableStateOf(TextSizeChoice.NORMAL) }
    var language by remember { mutableStateOf(AppLanguage.ID) }
    var initialized by remember { mutableStateOf(false) }

    // Seed the pickers from saved preferences once (re-opening from Profile).
    LaunchedEffect(current) {
        val c = current ?: return@LaunchedEffect
        if (!initialized) {
            theme = c.theme
            textSize = c.textSize
            language = c.language
            initialized = true
        }
    }

    // Live preview: the whole setup screen re-renders with the language, theme
    // and text size the user is choosing, so every choice is visible instantly.
    val previewStrings = remember(language) { Loc(language) }
    val previewDark = when (theme) {
        ThemeChoice.SYSTEM -> systemDark
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }
    RatigTheme(
        darkTheme = previewDark,
        textScale = textSize.scale,
    ) {
        CompositionLocalProvider(LocalStrings provides previewStrings) {
            SetupContent(
                strings = previewStrings,
                systemDark = systemDark,
                theme = theme,
                textSize = textSize,
                language = language,
                onThemeChange = { theme = it },
                onTextSizeChange = { textSize = it },
                onLanguageChange = { language = it },
                onSave = { viewModel.finish(theme, textSize, language, onDone) },
            )
        }
    }
}

@Composable
private fun SetupContent(
    strings: Loc,
    systemDark: Boolean,
    theme: ThemeChoice,
    textSize: TextSizeChoice,
    language: AppLanguage,
    onThemeChange: (ThemeChoice) -> Unit,
    onTextSizeChange: (TextSizeChoice) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onSave: () -> Unit,
) {

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.TouchApp, contentDescription = null, modifier = Modifier.size(34.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = strings.t(S.SETUP_TITLE),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = strings.t(S.SETUP_SUBTITLE),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---- Language (first, so the rest is readable) ----
            SectionTitle(strings.t(S.SETUP_LANGUAGE), strings.t(S.SETUP_LANGUAGE_SUB))
            AppLanguage.entries.forEach { option ->
                ChoiceCard(
                    icon = Icons.Rounded.Language,
                    title = option.label,
                    subtitle = option.code.uppercase(),
                    selected = language == option,
                    onClick = { onLanguageChange(option) },
                )
            }

            // ---- Theme ----
            SectionTitle(strings.t(S.SETUP_DISPLAY_MODE), strings.t(S.SETUP_DISPLAY_MODE_SUB))
            ThemeOption.entries.forEach { option ->
                ChoiceCard(
                    icon = option.icon,
                    title = strings.t(option.title),
                    subtitle = strings.t(option.subtitle),
                    selected = theme == option.choice,
                    onClick = { onThemeChange(option.choice) },
                )
            }
            if (theme == ThemeChoice.SYSTEM) {
                Text(
                    text = strings.t(
                        if (systemDark) S.SETUP_SYSTEM_DARK_HINT else S.SETUP_SYSTEM_LIGHT_HINT,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            // ---- Text size ----
            SectionTitle(strings.t(S.SETUP_TEXT_SIZE), strings.t(S.SETUP_TEXT_SIZE_SUB))
            TextSizeOption.entries.forEach { option ->
                ChoiceCard(
                    icon = Icons.Rounded.TextFields,
                    title = strings.t(option.title),
                    subtitle = strings.t(option.subtitle),
                    selected = textSize == option.choice,
                    onClick = { onTextSizeChange(option.choice) },
                    titleStyle = MaterialTheme.typography.titleMedium.copy(
                        fontSize = MaterialTheme.typography.titleMedium.fontSize * option.choice.scale,
                    ),
                )
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(strings.t(S.SETUP_SAVE_CONTINUE), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.ArrowForward, contentDescription = null)
            }
            Text(
                text = strings.t(S.SETUP_CHANGE_LATER),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private enum class ThemeOption(
    val choice: ThemeChoice,
    val icon: ImageVector,
    val title: S,
    val subtitle: S,
) {
    SYSTEM(ThemeChoice.SYSTEM, Icons.Rounded.SettingsBrightness, S.SETUP_THEME_SYSTEM, S.SETUP_THEME_SYSTEM_SUB),
    LIGHT(ThemeChoice.LIGHT, Icons.Rounded.LightMode, S.SETUP_THEME_LIGHT, S.SETUP_THEME_LIGHT_SUB),
    DARK(ThemeChoice.DARK, Icons.Rounded.DarkMode, S.SETUP_THEME_DARK, S.SETUP_THEME_DARK_SUB),
}

private enum class TextSizeOption(
    val choice: TextSizeChoice,
    val title: S,
    val subtitle: S,
) {
    NORMAL(TextSizeChoice.NORMAL, S.SETUP_TEXT_NORMAL, S.SETUP_TEXT_NORMAL_SUB),
    LARGE(TextSizeChoice.LARGE, S.SETUP_TEXT_LARGE, S.SETUP_TEXT_LARGE_SUB),
    EXTRA_LARGE(TextSizeChoice.EXTRA_LARGE, S.SETUP_TEXT_XLARGE, S.SETUP_TEXT_XLARGE_SUB),
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChoiceCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val border = if (selected) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
    } else {
        Modifier
    }
    Surface(
        color = container,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(border)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = titleStyle, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = LocalStrings.current.t(S.SETUP_SELECTED),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}
