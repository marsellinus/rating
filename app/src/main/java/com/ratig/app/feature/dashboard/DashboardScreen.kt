package com.ratig.app.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ratig.app.core.result.AppResult
import com.ratig.app.core.time.TimeProvider
import com.ratig.app.domain.model.AdminDashboardStats
import com.ratig.app.domain.model.ExaminerDashboardStats
import com.ratig.app.domain.model.ManagementDashboardStats
import com.ratig.app.domain.model.ReactionTrendPoint
import com.ratig.app.domain.model.RecentResult
import com.ratig.app.domain.model.UserRole
import com.ratig.app.domain.repository.AuthRepository
import com.ratig.app.domain.repository.DashboardRepository
import com.ratig.app.domain.repository.SessionState
import com.ratig.app.ui.components.BarChartSimple
import com.ratig.app.ui.components.DailyTrendChart
import com.ratig.app.ui.components.DonutClassificationChart
import com.ratig.app.ui.components.MetricTile
import com.ratig.app.ui.components.SeverityBadge
import com.ratig.app.ui.components.ReactionTrendChart
import com.ratig.app.ui.components.EmptyState
import com.ratig.app.ui.components.ErrorState
import com.ratig.app.ui.components.LoadingState
import com.ratig.app.ui.navigation.Routes
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Composable
fun DashboardRoute(
    onOpenSession: (String) -> Unit,
    onOpenWorker: (String) -> Unit,
    onOpenAdmin: (String) -> Unit,
    onStartIdentification: () -> Unit = {},
    onOpenSyncStatus: () -> Unit = {},
) {
    val viewModel: DashboardViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreen(
        state = state,
        onRetry = viewModel::refresh,
        onPeriodSelected = viewModel::setPeriod,
        onOpenSession = onOpenSession,
        onOpenWorker = onOpenWorker,
        onOpenAdmin = onOpenAdmin,
        onStartIdentification = onStartIdentification,
        onOpenSyncStatus = onOpenSyncStatus,
    )
}

@Composable
private fun DashboardScreen(
    state: DashboardViewModel.DashboardUiState,
    onRetry: () -> Unit,
    onPeriodSelected: (Int) -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenWorker: (String) -> Unit,
    onOpenAdmin: (String) -> Unit,
    onStartIdentification: () -> Unit,
    onOpenSyncStatus: () -> Unit,
) {
    val admin = state.admin
    val examiner = state.examiner
    val management = state.management
    when {
        state.loading -> LoadingState()
        state.error != null -> ErrorState(message = state.error, onRetry = onRetry)
        state.role == UserRole.SUPER_ADMIN && admin != null ->
            AdminDashboardContent(
                stats = admin,
                onOpenSession = onOpenSession,
                onOpenWorker = onOpenWorker,
                onOpenAdmin = onOpenAdmin,
            )
        state.role == UserRole.ADMIN && management != null ->
            ManagementDashboardContent(
                stats = management,
                trend = state.reactionTrend,
                periodDays = state.periodDays,
                periodFrom = state.periodFrom,
                periodTo = state.periodTo,
                onPeriodSelected = onPeriodSelected,
            )
        state.role == UserRole.USER && examiner != null ->
            ExaminerDashboardContent(stats = examiner, onOpenSession = onOpenSession, onStartIdentification = onStartIdentification)
        state.role == UserRole.USER -> WorkerHomeContent()
        else -> EmptyState(
            icon = Icons.Rounded.Info,
            title = "Dasbor tidak tersedia",
            description = "Peran akun Anda belum memiliki dasbor. Hubungi administrator.",
        )
    }
}

// ---------------------------------------------------------------- Admin

@Composable
private fun AdminDashboardContent(
    stats: AdminDashboardStats,
    onOpenSession: (String) -> Unit,
    onOpenWorker: (String) -> Unit,
    onOpenAdmin: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Dasbor Super Admin",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("Pekerja Aktif", stats.activeWorkers.toString(), Modifier.weight(1f))
            MetricTile("Total Pengguna", stats.totalUsers.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("Sesi Hari Ini", stats.sessionsToday.toString(), Modifier.weight(1f))
            MetricTile("Total Sesi", stats.totalSessions.toString(), Modifier.weight(1f))
        }
        SectionCard("Perlu Perhatian") {
            AttentionRow(Icons.Rounded.HourglassTop, "Sesi belum selesai", stats.unfinishedSessions)
            AttentionRow(
                Icons.Rounded.Assignment,
                "Tindak lanjut terbuka",
                stats.openFollowUps,
                onClick = { onOpenAdmin(Routes.FOLLOW_UPS) },
            )
            AttentionRow(
                Icons.Rounded.PersonAdd,
                "Persetujuan akun menunggu",
                stats.pendingApprovals,
                onClick = { onOpenAdmin(Routes.ADMIN_USERS) },
            )
        }
        SectionCard("Klasifikasi Hasil Pemeriksaan") {
            DonutClassificationChart(counts = stats.classificationCounts)
        }
        SectionCard("Sesi per Departemen") {
            BarChartSimple(data = stats.byDepartment)
        }
        SectionCard("Sesi per Shift") {
            BarChartSimple(data = stats.byShift)
        }
        SectionCard("Tren Sesi 14 Hari") {
            DailyTrendChart(data = stats.trend)
            Text(
                text = "Total sampel: ${stats.sampleSize} sesi dalam 14 hari terakhir",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------- Examiner

@Composable
private fun ExaminerDashboardContent(
    stats: ExaminerDashboardStats,
    onOpenSession: (String) -> Unit,
    onStartIdentification: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Beranda Pengguna",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("Jadwal Hari Ini", stats.scheduledToday.toString(), Modifier.weight(1f))
            MetricTile("Sesi Hari Ini", stats.sessionsToday.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("Sesi 7 Hari", stats.sessionsLast7Days.toString(), Modifier.weight(1f))
            MetricTile("Tindak Lanjut Terbuka", stats.openFollowUps.toString(), Modifier.weight(1f))
        }
        Button(
            onClick = onStartIdentification,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(Icons.Outlined.PersonSearch, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Pemeriksaan Baru")
        }

        SectionCard("Langkah Pemeriksaan") {
            StepText(1, "Buka menu Pekerja pada navigasi bawah, lalu pilih pekerja yang akan diperiksa.")
            StepText(2, "Tekan \"Mulai Pemeriksaan\" pada detail pekerja untuk membuat sesi baru.")
            StepText(3, "Ikuti tes waktu reaksi hingga seluruh percobaan selesai.")
            StepText(4, "Tinjau hasil pada layar hasil, lalu finalisasi sesi.")
        }
        SectionCard("Pemeriksaan Terakhir") {
            if (stats.recentResults.isEmpty()) {
                Text(
                    text = "Belum ada pemeriksaan yang diselesaikan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                stats.recentResults.forEach { result ->
                    ResultRow(result = result, onOpenSession = onOpenSession)
                }
            }
        }
    }
}

@Composable
private fun ResultRow(result: RecentResult, onOpenSession: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { onOpenSession(result.sessionId) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.workerName,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = formatIso(result.completedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SeverityBadge(
                label = result.classificationLabel ?: result.classificationCode ?: "Tanpa Kategori",
                severity = result.severity,
            )
            result.meanReactionTimeMs?.let { mean ->
                Text(
                    text = "${formatMs(mean)} ms rata-rata",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StepText(number: Int, text: String) {
    Row {
        Text(
            text = "$number.",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(20.dp),
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------- Management

@Composable
private fun ManagementDashboardContent(
    stats: ManagementDashboardStats,
    trend: List<ReactionTrendPoint>,
    periodDays: Int,
    periodFrom: LocalDate?,
    periodTo: LocalDate?,
    onPeriodSelected: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Dasbor Admin",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PeriodChip(days = 7, selectedDays = periodDays, onSelect = onPeriodSelected, modifier = Modifier.weight(1f))
            PeriodChip(days = 30, selectedDays = periodDays, onSelect = onPeriodSelected, modifier = Modifier.weight(1f))
            PeriodChip(days = 90, selectedDays = periodDays, onSelect = onPeriodSelected, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("Total Sesi", stats.totalSessions.toString(), Modifier.weight(1f))
            MetricTile("Sesi Hari Ini", stats.sessionsToday.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile(
                "Tindak Lanjut Terbuka",
                stats.openFollowUps.toString(),
                Modifier.weight(1f),
            )
        }
        // Caption selalu tampil: rentang tanggal + jumlah sampel.
        Text(
            text = "Periode: ${formatDay(periodFrom)} – ${formatDay(periodTo)} • Sampel: ${stats.sampleSize} sesi",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionCard("Tren Waktu Reaksi") {
            ReactionTrendChart(data = trend)
        }
        SectionCard("Klasifikasi Hasil Pemeriksaan") {
            DonutClassificationChart(counts = stats.classificationCounts)
        }
        SectionCard("Sesi per Departemen") {
            BarChartSimple(data = stats.byDepartment)
        }
        SectionCard("Sesi per Shift") {
            BarChartSimple(data = stats.byShift)
        }
    }
}

@Composable
private fun PeriodChip(
    days: Int,
    selectedDays: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selectedDays == days,
        onClick = { onSelect(days) },
        label = { Text("$days Hari") },
        modifier = modifier,
    )
}

// ---------------------------------------------------------------- Worker

@Composable
private fun WorkerHomeContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Beranda",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Panduan Pemeriksaan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = "Pemeriksaan waktu reaksi dilakukan oleh pemeriksa/petugas menggunakan perangkat ini di tempat kerja Anda.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Pastikan Anda beristirahat cukup sebelum pemeriksaan dan ikuti instruksi pemeriksa selama tes berlangsung.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Hasil pemeriksaan Anda akan tampil di menu Riwayat pada navigasi bawah setelah sesi selesai diproses.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Shared helpers

@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun AttentionRow(
    icon: ImageVector,
    label: String,
    count: Long,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (onClick != null) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatIso(iso: String?): String =
    iso?.let { raw -> runCatching { Instant.parse(raw) }.getOrNull() }
        ?.let { TimeProvider.formatForDisplay(it) }
        ?: "-"

private fun formatDay(day: LocalDate?): String =
    day?.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) ?: "-"

private fun formatMs(value: Double): String = String.format(java.util.Locale.US, "%.0f", value)

// ---------------------------------------------------------------- ViewModel

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    data class DashboardUiState(
        val loading: Boolean = true,
        val error: String? = null,
        val role: UserRole? = null,
        val admin: AdminDashboardStats? = null,
        val examiner: ExaminerDashboardStats? = null,
        val management: ManagementDashboardStats? = null,
        val reactionTrend: List<ReactionTrendPoint> = emptyList(),
        val periodDays: Int = DEFAULT_PERIOD_DAYS,
        val periodFrom: LocalDate? = null,
        val periodTo: LocalDate? = null,
    )

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.sessionState.collect { session ->
                val role = (session as? SessionState.Authenticated)?.profile?.role
                if (role != null && role != _uiState.value.role) {
                    _uiState.update {
                        it.copy(role = role, admin = null, examiner = null, management = null)
                    }
                    when (role) {
                        UserRole.SUPER_ADMIN -> loadAdmin()
                        UserRole.ADMIN -> loadManagement(_uiState.value.periodDays)
                        UserRole.USER -> loadExaminer()
                    }
                } else if (role == null && session is SessionState.Error) {
                    _uiState.update { it.copy(loading = false, error = session.message) }
                }
            }
        }
    }

    fun refresh() {
        when (_uiState.value.role) {
            UserRole.SUPER_ADMIN -> loadAdmin()
            UserRole.ADMIN -> loadManagement(_uiState.value.periodDays)
            UserRole.USER -> loadExaminer()
            else -> Unit
        }
    }

    fun setPeriod(days: Int) {
        if (days == _uiState.value.periodDays) return
        loadManagement(days)
    }

    private fun loadAdmin() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = dashboardRepository.adminStats()) {
                is AppResult.Success ->
                    _uiState.update { it.copy(loading = false, admin = result.value) }
                is AppResult.Failure ->
                    _uiState.update { it.copy(loading = false, error = result.error.userMessage) }
            }
        }
    }

    private fun loadExaminer() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = dashboardRepository.examinerStats()) {
                is AppResult.Success ->
                    _uiState.update { it.copy(loading = false, examiner = result.value) }
                is AppResult.Failure ->
                    _uiState.update { it.copy(loading = false, error = result.error.userMessage) }
            }
        }
    }

    private fun loadManagement(days: Int) {
        val to = TimeProvider.nowUtc()
        val from = to.minus(days - 1L, ChronoUnit.DAYS)
        _uiState.update {
            it.copy(
                loading = true,
                error = null,
                periodDays = days,
                periodFrom = TimeProvider.operationalDay(from),
                periodTo = TimeProvider.operationalDay(to),
            )
        }
        viewModelScope.launch {
            coroutineScope {
                val statsDeferred = async { dashboardRepository.managementStats(from, to) }
                val trendDeferred = async { dashboardRepository.reactionTrend(from, to, null) }
                val stats = statsDeferred.await()
                val trend = trendDeferred.await()
                _uiState.update { current ->
                    current.copy(
                        loading = false,
                        management = (stats as? AppResult.Success)?.value ?: current.management,
                        reactionTrend = (trend as? AppResult.Success)?.value ?: current.reactionTrend,
                        error = (
                            (stats as? AppResult.Failure)?.error
                                ?: (trend as? AppResult.Failure)?.error
                            )?.userMessage,
                    )
                }
            }
        }
    }

    companion object {
        const val DEFAULT_PERIOD_DAYS = 30
    }
}
