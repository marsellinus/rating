package com.ratig.app.feature.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ratig.app.core.config.AppConfig
import com.ratig.app.data.offline.AppPreferencesStore
import com.ratig.app.data.offline.RememberLoginStore
import com.ratig.app.domain.repository.AuthRepository
import com.ratig.app.domain.repository.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    appConfig: AppConfig,
    private val rememberLoginStore: RememberLoginStore,
    private val appPreferencesStore: AppPreferencesStore,
) : ViewModel() {

    val isConfigured: Boolean = appConfig.isConfigured

    val sessionState: StateFlow<SessionState> = authRepository.sessionState

    /** null = not yet known. */
    private val _firstRun = MutableStateFlow<FirstRunStep?>(null)
    val firstRun: StateFlow<FirstRunStep?> = _firstRun.asStateFlow()

    init {
        if (isConfigured) {
            viewModelScope.launch {
                val current = sessionState.value
                if (current is SessionState.Loading || current is SessionState.Error) {
                    authRepository.restoreSession()
                }
            }
        }
        viewModelScope.launch {
            // Order on a fresh install: setup -> tutorial -> login.
            val setupDone = appPreferencesStore.current().setupDone
            val onboardingDone = rememberLoginStore.current().onboardingDone
            _firstRun.value = when {
                !setupDone -> FirstRunStep.SETUP
                !onboardingDone -> FirstRunStep.ONBOARDING
                else -> FirstRunStep.DONE
            }
        }
    }
}

/** Which pre-login screen a signed-out user should see first. */
enum class FirstRunStep { SETUP, ONBOARDING, DONE }

/**
 * Decides the start destination from the server-verified session state.
 * Navigates exactly once: [navigated] guards against repeated state emissions.
 * A signed-out first-run user walks through setup -> tutorial -> login.
 */
@Composable
fun SplashRoute(
    onGoLogin: () -> Unit,
    onGoPending: () -> Unit,
    onGoHome: () -> Unit,
    onGoConfigError: () -> Unit,
    onGoOnboarding: () -> Unit,
    onGoSetup: () -> Unit,
) {
    val viewModel: SplashViewModel = hiltViewModel()
    var navigated by rememberSaveable { mutableStateOf(false) }

    if (!viewModel.isConfigured) {
        LaunchedEffect(Unit) {
            if (!navigated) {
                navigated = true
                onGoConfigError()
            }
        }
    } else {
        val state by viewModel.sessionState.collectAsStateWithLifecycle()
        val firstRun by viewModel.firstRun.collectAsStateWithLifecycle()
        LaunchedEffect(state, firstRun) {
            if (navigated) return@LaunchedEffect
            when (state) {
                SessionState.Loading -> Unit
                SessionState.Unauthenticated, is SessionState.Error -> {
                    // Wait until we know the first-run step.
                    val step = firstRun ?: return@LaunchedEffect
                    navigated = true
                    when (step) {
                        FirstRunStep.SETUP -> onGoSetup()
                        FirstRunStep.ONBOARDING -> onGoOnboarding()
                        FirstRunStep.DONE -> onGoLogin()
                    }
                }
                is SessionState.AwaitingApproval -> {
                    navigated = true
                    onGoPending()
                }
                is SessionState.Authenticated -> {
                    navigated = true
                    onGoHome()
                }
            }
        }
    }
    SplashContent()
}

@Composable
private fun SplashContent() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().padding(32.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.TouchApp,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "RATIG",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Reaction Time Fatigue",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                )
            }
        }
    }
}

/**
 * Shown when local.properties did not provide the required build-time keys.
 */
@Composable
fun ConfigErrorRoute(onRetry: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = "Konfigurasi belum lengkap",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Aplikasi membutuhkan tiga nilai konfigurasi sebelum dapat dijalankan. " +
                    "Tambahkan baris berikut ke file local.properties di root proyek, " +
                    "lalu build ulang aplikasi:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        "supabase.url=https://<project-ref>.supabase.co",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        "supabase.anonKey=<anon/publishable key>",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        "google.webClientId=<web client id>.apps.googleusercontent.com",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Text(
                text = "Setelah selesai, sentuh \"Coba Lagi\" untuk memeriksa konfigurasi kembali.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Coba Lagi")
            }
        }
    }
}
