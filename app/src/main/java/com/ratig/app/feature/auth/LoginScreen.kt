package com.ratig.app.feature.auth

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ratig.app.BuildConfig
import com.ratig.app.core.result.AppResult
import com.ratig.app.domain.model.AccountStatus
import com.ratig.app.domain.model.UserProfile
import com.ratig.app.domain.repository.AuthRepository
import com.ratig.app.domain.repository.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** True only in debug builds: email/password login for device testing. */
    val showPasswordLogin: Boolean = BuildConfig.DEBUG

    /** True when a Google Web client id is configured (see AppConfig). */
    val googleAvailable: Boolean = authRepository.isGoogleAvailable

    val sessionState: StateFlow<SessionState> = authRepository.sessionState

    fun signInWithGoogle(context: Context) {
        if (_uiState.value.loading) return
        _uiState.value = UiState(loading = true)
        viewModelScope.launch {
            val result = authRepository.signInWithGoogle(context)
            _uiState.value = when (result) {
                is AppResult.Success -> UiState(loading = false)
                is AppResult.Failure -> UiState(loading = false, error = result.error.userMessage)
            }
        }
    }

    fun signInWithPassword(email: String, password: String) {
        if (_uiState.value.loading) return
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = UiState(error = "Email dan kata sandi wajib diisi.")
            return
        }
        _uiState.value = UiState(loading = true)
        viewModelScope.launch {
            val result = authRepository.signInWithPassword(email.trim(), password)
            _uiState.value = when (result) {
                is AppResult.Success -> UiState(loading = false)
                is AppResult.Failure -> UiState(loading = false, error = result.error.userMessage)
            }
        }
    }
}

@Composable
fun LoginRoute(onSignedIn: () -> Unit, onPending: () -> Unit) {
    val viewModel: LoginViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()

    var navigated by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sessionState) {
        if (navigated) return@LaunchedEffect
        when (sessionState) {
            is SessionState.Authenticated -> {
                navigated = true
                onSignedIn()
            }
            is SessionState.AwaitingApproval -> {
                navigated = true
                onPending()
            }
            else -> Unit
        }
    }

    LoginContent(
        uiState = uiState,
        onSignIn = viewModel::signInWithGoogle,
        onSignInWithPassword = viewModel::signInWithPassword,
        showPasswordLogin = viewModel.showPasswordLogin,
        googleAvailable = viewModel.googleAvailable,
    )
}

@Composable
private fun LoginContent(
    uiState: LoginViewModel.UiState,
    onSignIn: (Context) -> Unit,
    onSignInWithPassword: (String, String) -> Unit,
    showPasswordLogin: Boolean,
    googleAvailable: Boolean,
) {
    val context = LocalContext.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().padding(24.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.TouchApp,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "RATIG",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Reaction Time Fatigue",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Pemeriksaan kelelahan pekerja berbasis waktu reaksi. " +
                            "Masuk menggunakan akun Google Anda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (uiState.error != null) {
                        Text(
                            text = uiState.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (googleAvailable) {
                        Button(
                            onClick = { onSignIn(context) },
                            enabled = !uiState.loading,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            if (uiState.loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Memproses...")
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Login,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Masuk dengan Google")
                            }
                        }
                    }

                    if (showPasswordLogin) {
                        if (googleAvailable) {
                            Text(
                                text = "atau",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email") },
                            singleLine = true,
                            enabled = !uiState.loading,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Kata sandi") },
                            singleLine = true,
                            enabled = !uiState.loading,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { onSignInWithPassword(email, password) },
                            enabled = !uiState.loading,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text("Masuk (debug)")
                        }
                        Text(
                            text = "Login email/kata sandi hanya tersedia pada build debug.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }

                    if (!googleAvailable && !showPasswordLogin) {
                        Text(
                            text = "Login Google belum dikonfigurasi. Hubungi administrator.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@HiltViewModel
class PendingApprovalViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    data class UiState(
        val refreshing: Boolean = false,
        val signingOut: Boolean = false,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val sessionState: StateFlow<SessionState> = authRepository.sessionState

    private val _signOutCompleted = MutableStateFlow(false)
    val signOutCompleted: StateFlow<Boolean> = _signOutCompleted.asStateFlow()

    init {
        refreshProfile()
    }

    /** Re-checks approval status on the server and updates the session state. */
    fun refreshProfile() {
        if (_uiState.value.refreshing) return
        _uiState.value = _uiState.value.copy(refreshing = true, error = null)
        viewModelScope.launch {
            val result = authRepository.refreshProfile()
            _uiState.value = when (result) {
                is AppResult.Success -> _uiState.value.copy(refreshing = false)
                is AppResult.Failure ->
                    _uiState.value.copy(refreshing = false, error = result.error.userMessage)
            }
        }
    }

    fun signOut() {
        if (_uiState.value.signingOut) return
        _uiState.value = _uiState.value.copy(signingOut = true)
        viewModelScope.launch {
            authRepository.signOut()
            _signOutCompleted.value = true
        }
    }
}

@Composable
fun PendingApprovalRoute(onApproved: () -> Unit, onSignOut: () -> Unit) {
    val viewModel: PendingApprovalViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val signOutCompleted by viewModel.signOutCompleted.collectAsStateWithLifecycle()

    LaunchedEffect(sessionState) {
        if (sessionState is SessionState.Authenticated) onApproved()
    }
    LaunchedEffect(signOutCompleted) {
        if (signOutCompleted) onSignOut()
    }

    // Auto-refresh when the screen becomes visible again (returns from the
    // background, e.g. after the administrator approved the account).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var firstResume = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (firstResume) {
                    firstResume = false
                } else {
                    viewModel.refreshProfile()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val profile = (sessionState as? SessionState.AwaitingApproval)?.profile
    PendingApprovalContent(
        profile = profile,
        uiState = uiState,
        onCheckStatus = viewModel::refreshProfile,
        onSignOut = viewModel::signOut,
    )
}

@Composable
private fun PendingApprovalContent(
    profile: UserProfile?,
    uiState: PendingApprovalViewModel.UiState,
    onCheckStatus: () -> Unit,
    onSignOut: () -> Unit,
) {
    val suspended = profile?.accountStatus == AccountStatus.SUSPENDED
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Spacer(Modifier.height(32.dp))
            Icon(
                imageVector = if (suspended) Icons.Rounded.Block else Icons.Rounded.HourglassTop,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = if (suspended) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Text(
                text = if (suspended) "Akun dinonaktifkan" else "Menunggu persetujuan administrator",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (suspended) {
                    "Akun Anda dinonaktifkan oleh administrator dan tidak dapat digunakan."
                } else {
                    "Akun Google Anda sudah terdaftar, namun belum diaktifkan. " +
                        "Akses aplikasi dibuka setelah administrator menyetujui akun Anda."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (profile != null) {
                Text(
                    text = "${profile.fullName} - ${profile.email}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            uiState.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            if (!suspended) {
                Button(
                    onClick = onCheckStatus,
                    enabled = !uiState.refreshing,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    if (uiState.refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Memeriksa...")
                    } else {
                        Text("Periksa Status")
                    }
                }
            }
            OutlinedButton(
                onClick = onSignOut,
                enabled = !uiState.signingOut,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(if (uiState.signingOut) "Keluar..." else "Keluar")
            }
        }
    }
}
