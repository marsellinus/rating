package com.ratig.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ratig.app.core.device.DeviceMetadata
import com.ratig.app.core.result.AppResult
import com.ratig.app.domain.model.UserProfile
import com.ratig.app.domain.model.UserRole
import com.ratig.app.domain.repository.AuthRepository
import com.ratig.app.ui.components.ConfirmDialog
import com.ratig.app.ui.components.ErrorState
import com.ratig.app.ui.components.LoadingState
import com.ratig.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    /** App version shown in the profile footer. */
    val appVersion: String = DeviceMetadata.current().appVersionName

    data class UiState(
        val loading: Boolean = true,
        val profile: UserProfile? = null,
        val error: String? = null,
        val signingOut: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _signOutCompleted = MutableStateFlow(false)
    val signOutCompleted: StateFlow<Boolean> = _signOutCompleted.asStateFlow()

    init {
        refreshProfile()
    }

    /** Reloads the server-verified profile (role/status may have changed). */
    fun refreshProfile() {
        _uiState.value = _uiState.value.copy(
            loading = _uiState.value.profile == null,
            error = null,
        )
        viewModelScope.launch {
            when (val result = authRepository.refreshProfile()) {
                is AppResult.Success ->
                    _uiState.value = UiState(loading = false, profile = result.value)
                is AppResult.Failure ->
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = result.error.userMessage,
                    )
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

private data class ProfileMenuItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
    val adminOnly: Boolean = false,
    val superAdminOnly: Boolean = false,
)

private val menuItems = listOf(
    ProfileMenuItem("Jadwal & Shift", Icons.Outlined.CalendarMonth, Routes.SCHEDULES),
    ProfileMenuItem("Tindak Lanjut", Icons.Outlined.FactCheck, Routes.FOLLOW_UPS),
    ProfileMenuItem("Laporan", Icons.Outlined.BarChart, Routes.REPORTS),
    // Admin + super admin: user management and monitoring.
    ProfileMenuItem("Pengguna", Icons.Outlined.ManageAccounts, Routes.ADMIN_USERS, adminOnly = true),
    ProfileMenuItem("Audit Log", Icons.Outlined.ReceiptLong, Routes.ADMIN_AUDIT, adminOnly = true),
    // Super admin only: system configuration / master data.
    ProfileMenuItem("Protokol Uji", Icons.Outlined.Science, Routes.ADMIN_PROTOCOLS, superAdminOnly = true),
    ProfileMenuItem("Aturan Klasifikasi", Icons.Outlined.Rule, Routes.ADMIN_RULES, superAdminOnly = true),
    ProfileMenuItem("Data Master", Icons.Outlined.Storage, Routes.ADMIN_MASTER_DATA, superAdminOnly = true),
)

private fun roleLabel(role: UserRole): String = when (role) {
    UserRole.SUPER_ADMIN -> "Super Admin"
    UserRole.ADMIN -> "Administrator"
    UserRole.USER -> "Pengguna"
}

@Composable
fun ProfileRoute(onSignOut: () -> Unit, onOpenAdmin: (String) -> Unit) {
    val viewModel: ProfileViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val signOutCompleted by viewModel.signOutCompleted.collectAsStateWithLifecycle()

    var showSignOutDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(signOutCompleted) {
        if (signOutCompleted) onSignOut()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val profile = uiState.profile
        when {
            uiState.loading -> LoadingState()
            profile != null -> ProfileContent(
                profile = profile,
                appVersion = viewModel.appVersion,
                signingOut = uiState.signingOut,
                onOpenAdmin = onOpenAdmin,
                onRequestSignOut = { showSignOutDialog = true },
            )
            else -> ErrorState(
                message = uiState.error ?: "Profil tidak tersedia.",
                onRetry = viewModel::refreshProfile,
            )
        }
    }

    if (showSignOutDialog) {
        ConfirmDialog(
            title = "Keluar dari akun?",
            message = "Sesi Anda akan diakhiri. Anda dapat masuk kembali kapan saja.",
            confirmLabel = "Keluar",
            destructive = true,
            onConfirm = {
                showSignOutDialog = false
                viewModel.signOut()
            },
            onDismiss = { showSignOutDialog = false },
        )
    }
}

@Composable
private fun ProfileContent(
    profile: UserProfile,
    appVersion: String,
    signingOut: Boolean,
    onOpenAdmin: (String) -> Unit,
    onRequestSignOut: () -> Unit,
) {
    val isAdmin = profile.role == UserRole.ADMIN || profile.role == UserRole.SUPER_ADMIN
    val isSuperAdmin = profile.role == UserRole.SUPER_ADMIN
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.fullName.ifBlank { "Pengguna" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = profile.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    RoleChip(role = profile.role)
                }
            }
        }

        Text(
            text = "Menu",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
        ) {
            Column {
                val visibleItems = menuItems.filter { item ->
                    (!item.adminOnly || isAdmin) && (!item.superAdminOnly || isSuperAdmin)
                }
                visibleItems.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider()
                    ProfileMenuRow(
                        item = item,
                        onClick = { onOpenAdmin(item.route) },
                    )
                }
            }
        }

        OutlinedButton(
            onClick = onRequestSignOut,
            enabled = !signingOut,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (signingOut) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text("Keluar...")
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Keluar")
            }
        }

        Text(
            text = "Versi aplikasi $appVersion",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RoleChip(role: UserRole) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = roleLabel(role),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ProfileMenuRow(
    item: ProfileMenuItem,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
