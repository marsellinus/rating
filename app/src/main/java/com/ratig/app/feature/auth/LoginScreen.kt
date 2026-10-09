package com.ratig.app.feature.auth

import android.content.Context
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ratig.app.core.i18n.LocalStrings
import com.ratig.app.core.i18n.S
import com.ratig.app.core.result.AppResult
import com.ratig.app.data.offline.RememberLoginStore
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
    private val rememberLoginStore: RememberLoginStore,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val error: String? = null,
        val showPassword: Boolean = false,
        val rememberMe: Boolean = true,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Google sign-in is only offered when a client id is configured. */
    val googleAvailable: Boolean = authRepository.isGoogleAvailable

    val sessionState: StateFlow<SessionState> = authRepository.sessionState

    init {
        // Pre-fill the last used email when "Ingat saya" was checked.
        viewModelScope.launch {
            val remembered = rememberLoginStore.current()
            if (remembered.remember) {
                _uiState.value = _uiState.value.copy(rememberMe = true)
                _rememberedEmail.value = remembered.email
            }
        }
    }

    private val _rememberedEmail = MutableStateFlow("")
    val rememberedEmail: StateFlow<String> = _rememberedEmail.asStateFlow()

    fun setRememberMe(remember: Boolean) {
        _uiState.value = _uiState.value.copy(rememberMe = remember)
    }

    fun togglePasswordVisibility() {
        _uiState.value = _uiState.value.copy(showPassword = !_uiState.value.showPassword)
    }

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
            _uiState.value = _uiState.value.copy(error = "Email dan kata sandi wajib diisi.")
            return
        }
        _uiState.value = UiState(loading = true, showPassword = _uiState.value.showPassword)
        viewModelScope.launch {
            val result = authRepository.signInWithPassword(email.trim(), password)
            when (result) {
                is AppResult.Success -> {
                    // Persist the "Ingat saya" choice only on a successful sign-in.
                    rememberLoginStore.set(_uiState.value.rememberMe, email.trim())
                    _uiState.value = UiState(loading = false)
                }
                is AppResult.Failure -> _uiState.value = UiState(
                    loading = false,
                    error = result.error.userMessage,
                    showPassword = _uiState.value.showPassword,
                    rememberMe = _uiState.value.rememberMe,
                )
            }
        }
    }
}

@Composable
fun LoginRoute(onSignedIn: () -> Unit, onPending: () -> Unit) {
    val viewModel: LoginViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val rememberedEmail by viewModel.rememberedEmail.collectAsStateWithLifecycle()

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
        rememberedEmail = rememberedEmail,
        onSignIn = viewModel::signInWithGoogle,
        onSignInWithPassword = viewModel::signInWithPassword,
        onTogglePassword = viewModel::togglePasswordVisibility,
        onRememberChange = viewModel::setRememberMe,
        googleAvailable = viewModel.googleAvailable,
    )
}

@Composable
private fun LoginContent(
    uiState: LoginViewModel.UiState,
    rememberedEmail: String,
    onSignIn: (Context) -> Unit,
    onSignInWithPassword: (String, String) -> Unit,
    onTogglePassword: () -> Unit,
    onRememberChange: (Boolean) -> Unit,
    googleAvailable: Boolean,
) {
    val context = LocalContext.current
    val strings = LocalStrings.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    // Pre-fill the remembered email once it loads (never overwrite typing).
    LaunchedEffect(rememberedEmail) {
        if (rememberedEmail.isNotBlank() && email.isBlank()) email = rememberedEmail
    }
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
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.TouchApp,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = strings.t(S.LOGIN_TITLE),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = strings.t(S.LOGIN_SUBTITLE),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = strings.t(S.LOGIN_HINT),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    if (uiState.error != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = uiState.error,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                            )
                        }
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(strings.t(S.LOGIN_EMAIL)) },
                        placeholder = { Text(strings.t(S.LOGIN_EMAIL_PLACEHOLDER)) },
                        singleLine = true,
                        enabled = !uiState.loading,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(strings.t(S.LOGIN_PASSWORD)) },
                        singleLine = true,
                        enabled = !uiState.loading,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        visualTransformation = if (uiState.showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = onTogglePassword) {
                                Icon(
                                    imageVector = if (uiState.showPassword) {
                                        Icons.Rounded.VisibilityOff
                                    } else {
                                        Icons.Rounded.Visibility
                                    },
                                    contentDescription = strings.t(
                                        if (uiState.showPassword) S.LOGIN_HIDE_PASSWORD else S.LOGIN_SHOW_PASSWORD,
                                    ),
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { onSignInWithPassword(email, password) },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRememberChange(!uiState.rememberMe) },
                    ) {
                        Checkbox(
                            checked = uiState.rememberMe,
                            onCheckedChange = onRememberChange,
                        )
                        Text(
                            text = strings.t(S.LOGIN_REMEMBER),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    Button(
                        onClick = { onSignInWithPassword(email, password) },
                        enabled = !uiState.loading,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        if (uiState.loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(strings.t(S.LOGIN_PROCESSING), style = MaterialTheme.typography.titleMedium)
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Login,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(strings.t(S.LOGIN_BUTTON), style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    if (googleAvailable) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "atau",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { onSignIn(context) },
                            enabled = !uiState.loading,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text(strings.t(S.LOGIN_GOOGLE))
                        }
                    }

                    Text(
                        text = strings.t(S.LOGIN_NO_ACCOUNT),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
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
