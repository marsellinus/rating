package com.ratig.app.feature.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ratig.app.core.result.onFailure
import com.ratig.app.core.result.onSuccess
import com.ratig.app.domain.model.AccountStatus
import com.ratig.app.domain.model.UserProfile
import com.ratig.app.domain.model.UserRole
import com.ratig.app.domain.repository.CreateUserRequest
import com.ratig.app.domain.repository.ProfileRepository
import com.ratig.app.ui.components.ConfirmDialog
import com.ratig.app.ui.components.EmptyState
import com.ratig.app.ui.components.ErrorState
import com.ratig.app.ui.components.LoadingState
import com.ratig.app.ui.components.SeverityBadge
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Segmented filter tabs on the users screen. */
enum class UsersTab(val label: String, val status: AccountStatus?) {
    PENDING("Pending", AccountStatus.PENDING),
    ACTIVE("Aktif", AccountStatus.ACTIVE),
    SUSPENDED("Nonaktif", AccountStatus.SUSPENDED),
    ALL("Semua", null),
}

data class UsersUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val tab: UsersTab = UsersTab.PENDING,
    val query: String = "",
    val users: List<UserProfile> = emptyList(),
    val ownUserId: String? = null,
    val ownRole: UserRole? = null,
    val creating: Boolean = false,
    val actionMessage: String? = null,
    val actionError: String? = null,
)

@HiltViewModel
class UsersViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    supabase: SupabaseClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        UsersUiState(ownUserId = supabase.auth.currentUserOrNull()?.id),
    )
    val uiState: StateFlow<UsersUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        load()
        loadOwnRole()
    }

    private fun loadOwnRole() {
        val ownId = _uiState.value.ownUserId ?: return
        viewModelScope.launch {
            profileRepository.getProfile(ownId)
                .onSuccess { profile -> _uiState.update { it.copy(ownRole = profile.role) } }
        }
    }

    fun refresh() = load()

    fun selectTab(tab: UsersTab) {
        _uiState.update { it.copy(tab = tab) }
        load()
    }

    /** Debounced search; empty queries list all users for the active tab. */
    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            load()
        }
    }

    fun approve(user: UserProfile) = setAccountStatus(user, AccountStatus.ACTIVE, "Akun ${user.fullName} disetujui.")

    fun suspendUser(user: UserProfile) {
        setAccountStatus(user, AccountStatus.SUSPENDED, "Akun ${user.fullName} dinonaktifkan.")
    }

    fun reactivate(user: UserProfile) {
        setAccountStatus(user, AccountStatus.ACTIVE, "Akun ${user.fullName} diaktifkan kembali.")
    }

    fun setRole(user: UserProfile, role: UserRole) {
        viewModelScope.launch {
            profileRepository.setRole(user.id, role)
                .onSuccess { _uiState.update { it.copy(actionMessage = "Role ${user.fullName} diubah.") } }
                .onSuccess { load() }
                .onFailure { error -> _uiState.update { it.copy(actionError = error.userMessage) } }
        }
    }

    /** Creates a new account via the admin-create-user Edge Function. */
    fun createUser(request: CreateUserRequest) {
        if (_uiState.value.creating) return
        _uiState.update { it.copy(creating = true, actionError = null) }
        viewModelScope.launch {
            profileRepository.createUser(request)
                .onSuccess { created ->
                    _uiState.update {
                        it.copy(
                            creating = false,
                            actionMessage = if (created.inviteSent) {
                                "Undangan dikirim ke ${created.email}."
                            } else {
                                "Akun ${created.fullName} berhasil dibuat."
                            },
                        )
                    }
                    load()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(creating = false, actionError = error.userMessage) }
                }
        }
    }

    fun consumeActionMessage() = _uiState.update { it.copy(actionMessage = null) }

    fun consumeActionError() = _uiState.update { it.copy(actionError = null) }

    private fun setAccountStatus(user: UserProfile, status: AccountStatus, successMessage: String) {
        viewModelScope.launch {
            profileRepository.setAccountStatus(user.id, status)
                .onSuccess { _uiState.update { it.copy(actionMessage = successMessage) } }
                .onSuccess { load() }
                .onFailure { error -> _uiState.update { it.copy(actionError = error.userMessage) } }
        }
    }

    private fun load() {
        val state = _uiState.value
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            profileRepository.listUsers(
                status = state.tab.status,
                query = state.query.trim().takeIf { it.isNotEmpty() },
                limit = 100,
                offset = 0,
            )
                .onSuccess { users -> _uiState.update { it.copy(loading = false, users = users) } }
                .onFailure { error -> _uiState.update { it.copy(loading = false, error = error.userMessage) } }
        }
    }
}

fun roleLabel(role: UserRole): String = when (role) {
    UserRole.SUPER_ADMIN -> "Super Admin"
    UserRole.ADMIN -> "Admin"
    UserRole.USER -> "Pengguna"
}

fun accountStatusLabel(status: AccountStatus): String = when (status) {
    AccountStatus.PENDING -> "Menunggu"
    AccountStatus.ACTIVE -> "Aktif"
    AccountStatus.SUSPENDED -> "Nonaktif"
}

fun accountStatusSeverity(status: AccountStatus): Int = when (status) {
    AccountStatus.PENDING -> 3
    AccountStatus.ACTIVE -> 1
    AccountStatus.SUSPENDED -> 4
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersRoute(
    onBack: () -> Unit,
    viewModel: UsersViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var approvingUser by remember { mutableStateOf<UserProfile?>(null) }
    var suspendingUser by remember { mutableStateOf<UserProfile?>(null) }
    var reactivatingUser by remember { mutableStateOf<UserProfile?>(null) }
    var roleChangeUser by remember { mutableStateOf<UserProfile?>(null) }
    var showCreateUser by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.actionMessage) {
        uiState.actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeActionMessage()
        }
    }
    LaunchedEffect(uiState.actionError) {
        uiState.actionError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeActionError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manajemen Pengguna") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateUser = true }) {
                Icon(Icons.Rounded.PersonAdd, contentDescription = "Tambah pengguna")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::setQuery,
                label = { Text("Cari nama atau email") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                UsersTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = uiState.tab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = UsersTab.entries.size),
                    ) {
                        Text(tab.label)
                    }
                }
            }

            when {
                uiState.loading -> LoadingState()
                uiState.error != null -> ErrorState(message = uiState.error!!, onRetry = viewModel::refresh)
                uiState.users.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.ManageAccounts,
                    title = "Tidak ada pengguna",
                    description = "Tidak ada pengguna pada filter ini.",
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.users, key = { it.id }) { user ->
                        UserRowCard(
                            user = user,
                            isOwnAccount = user.id == uiState.ownUserId,
                            onApprove = { approvingUser = user },
                            onSuspend = { suspendingUser = user },
                            onReactivate = { reactivatingUser = user },
                            onChangeRole = { roleChangeUser = user },
                        )
                    }
                }
            }
        }
    }

    approvingUser?.let { user ->
        ConfirmDialog(
            title = "Setujui akun?",
            message = "${user.fullName} (${user.email}) akan mendapat akses penuh sesuai rolenya.",
            confirmLabel = "Setujui",
            onConfirm = {
                viewModel.approve(user)
                approvingUser = null
            },
            onDismiss = { approvingUser = null },
        )
    }
    suspendingUser?.let { user ->
        ConfirmDialog(
            title = "Nonaktifkan akun?",
            message = "${user.fullName} tidak akan dapat masuk sampai diaktifkan kembali.",
            confirmLabel = "Nonaktifkan",
            destructive = true,
            onConfirm = {
                viewModel.suspendUser(user)
                suspendingUser = null
            },
            onDismiss = { suspendingUser = null },
        )
    }
    reactivatingUser?.let { user ->
        ConfirmDialog(
            title = "Aktifkan kembali akun?",
            message = "${user.fullName} akan dapat masuk kembali ke aplikasi.",
            confirmLabel = "Aktifkan",
            onConfirm = {
                viewModel.reactivate(user)
                reactivatingUser = null
            },
            onDismiss = { reactivatingUser = null },
        )
    }
    roleChangeUser?.let { user ->
        RoleChangeDialog(
            user = user,
            onDismiss = { roleChangeUser = null },
            onConfirmed = { role ->
                viewModel.setRole(user, role)
                roleChangeUser = null
            },
        )
    }
    if (showCreateUser) {
        CreateUserDialog(
            creating = uiState.creating,
            canCreateAdmin = uiState.ownRole == UserRole.SUPER_ADMIN,
            onDismiss = { if (!uiState.creating) showCreateUser = false },
            onSubmit = { request ->
                viewModel.createUser(request)
                showCreateUser = false
            },
        )
    }
}

@Composable
private fun UserRowCard(
    user: UserProfile,
    isOwnAccount: Boolean,
    onApprove: () -> Unit,
    onSuspend: () -> Unit,
    onReactivate: () -> Unit,
    onChangeRole: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.fullName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    if (isOwnAccount) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "(Akun Anda)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = roleLabel(user.role),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            SeverityBadge(
                label = accountStatusLabel(user.accountStatus),
                severity = accountStatusSeverity(user.accountStatus),
            )
            // Self-service guard: admins must not change their own account.
            IconButton(onClick = { menuOpen = true }, enabled = !isOwnAccount) {
                Icon(Icons.Rounded.MoreVert, contentDescription = if (isOwnAccount) null else "Aksi pengguna")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (user.accountStatus == AccountStatus.PENDING) {
                    DropdownMenuItem(
                        text = { Text("Setujui") },
                        onClick = { menuOpen = false; onApprove() },
                    )
                }
                if (user.accountStatus == AccountStatus.ACTIVE) {
                    DropdownMenuItem(
                        text = { Text("Nonaktifkan") },
                        onClick = { menuOpen = false; onSuspend() },
                    )
                }
                if (user.accountStatus == AccountStatus.SUSPENDED) {
                    DropdownMenuItem(
                        text = { Text("Aktifkan kembali") },
                        onClick = { menuOpen = false; onReactivate() },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Ubah Role") },
                    onClick = { menuOpen = false; onChangeRole() },
                )
            }
        }
    }
}

@Composable
private fun RoleChangeDialog(
    user: UserProfile,
    onDismiss: () -> Unit,
    onConfirmed: (UserRole) -> Unit,
) {
    var selectedRole by remember { mutableStateOf(user.role) }
    var showConfirm by remember { mutableStateOf(false) }

    if (showConfirm) {
        ConfirmDialog(
            title = "Ubah role pengguna?",
            message = "Tindakan ini mengubah hak akses ${user.fullName} menjadi ${roleLabel(selectedRole)}.",
            confirmLabel = "Ya, Ubah Role",
            onConfirm = { onConfirmed(selectedRole) },
            onDismiss = { showConfirm = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ubah Role") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                UserRole.entries.forEach { role ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedRole = role }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedRole == role,
                            onClick = { selectedRole = role },
                        )
                        Text(roleLabel(role), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { showConfirm = true },
                enabled = selectedRole != user.role,
            ) { Text("Lanjut") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
    )
}

@Composable
private fun CreateUserDialog(
    creating: Boolean,
    canCreateAdmin: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (CreateUserRequest) -> Unit,
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(UserRole.USER) }
    var status by remember { mutableStateOf(AccountStatus.ACTIVE) }
    var sendInvite by remember { mutableStateOf(true) }
    var localError by remember { mutableStateOf<String?>(null) }

    // Admins (non super) can only create the "user" role.
    val selectableRoles = if (canCreateAdmin) UserRole.entries.toList() else listOf(UserRole.USER)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Pengguna") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it; localError = null },
                    label = { Text("Nama lengkap") },
                    singleLine = true,
                    enabled = !creating,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; localError = null },
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = !creating,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Peran", style = MaterialTheme.typography.labelMedium)
                selectableRoles.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { role = option }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = role == option, onClick = { role = option })
                        Text(roleLabel(option), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("Status akun", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = status == AccountStatus.ACTIVE,
                        onClick = { status = AccountStatus.ACTIVE },
                    )
                    Text("Aktif", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(12.dp))
                    RadioButton(
                        selected = status == AccountStatus.PENDING,
                        onClick = { status = AccountStatus.PENDING },
                    )
                    Text("Menunggu", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = sendInvite, onCheckedChange = { sendInvite = it })
                    Text(
                        text = "Kirim undangan email (pengguna mengatur kata sandi sendiri)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (!sendInvite) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; localError = null },
                        label = { Text("Kata sandi sementara (min. 8)") },
                        singleLine = true,
                        enabled = !creating,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                localError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !creating,
                onClick = {
                    when {
                        fullName.trim().length < 2 ->
                            localError = "Nama lengkap wajib diisi."
                        !email.contains("@") || !email.contains(".") ->
                            localError = "Email tidak valid."
                        !sendInvite && password.length < 8 ->
                            localError = "Kata sandi minimal 8 karakter."
                        else -> onSubmit(
                            CreateUserRequest(
                                email = email.trim(),
                                fullName = fullName.trim(),
                                role = role,
                                accountStatus = status,
                                password = if (sendInvite) null else password,
                            ),
                        )
                    }
                },
            ) { Text(if (creating) "Menyimpan..." else "Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !creating) { Text("Batal") }
        },
    )
}
