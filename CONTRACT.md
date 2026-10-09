# RATIG — Feature Implementation Contract (agents)

Shared rules for all feature work. Read fully before writing code.
Foundation already exists: Gradle, theme (`ui/theme`), shared components (`ui/components/States.kt`),
domain models (`domain/model/*`), repository interfaces (`domain/repository/*`), pure logic
(`domain/usecase/MetricsCalculator.kt`, `FatigueClassifier.kt`), DI (`core/di/AppModule.kt`),
navigation (`ui/navigation/Routes.kt`, `RatigNavHost.kt`).

**Google sign-in approach (auth agent):** androidx Credential Manager (`androidx.credentials`) +
`com.google.android.libraries.identity.googleid.GoogleIdTokenCredential` to obtain an ID token
(serverClientId from `AppConfig.googleWebClientId`), then exchange it with
`supabase.auth.signInWith(IDToken)` from auth-kt using `IdTokenConfig`:
```kotlin
supabase.auth.signInWith(IDToken) {
    idToken = googleIdToken
    provider = Google   // io.github.jan.supabase.auth.providers.Google
}
```
Handle `GetCredentialException` / `NoCredentialException` (no account or cancelled) and network
errors as distinct user-facing outcomes.

## Global rules
- Language: Kotlin, package root `com.ratig.app`. UI copy in **Bahasa Indonesia** (concise, professional).
- Every repository method returns `AppResult<T>` (`core/result/AppResult.kt`); wrap bodies in
  `AppResult.of { ... }` so exceptions map to `AppError` automatically.
- DTOs live in `data/remote/dto/`, `@Serializable`, `@SerialName` snake_case exactly matching DB
  columns. Map DTO↔domain in the same DTO file (`fun toDomain()` / companion `fromDomain()`).
  Timestamps: `kotlinx.datetime`? NO — use `java.time.Instant` with a custom serializer:
  `@Serializable(with = InstantAsStringSerializer::class)` — create
  `core/json/InstantAsStringSerializer.kt` ONCE (object : KSerializer<Instant>, string ISO-8601
  via `Instant.parse`/`toString`); other agents will reuse it. Nullable timestamps serialize as omitted.
- PostgREST usage pattern:
  ```kotlin
  val dtos = supabase.postgrest["workers"].select {
      eq("active_status", true)
      if (q != null) ilike("full_name", "%$q%")
      range(offset, offset + limit - 1)
  }.decodeList<WorkerDto>()
  ```
  Insert: `.insert(dto) { select() }` returns created row. Update: `.update({ set("full_name", v) }) { eq("id", id) }`.
  RPC: `supabase.postgrest.rpc("fn_name", jsonArgs)` — `decodeAs<JsonElement>()` then parse with `JsonCodec`.
- ViewModels: `@HiltViewModel class XViewModel @Inject constructor(...)`; expose
  `data class UiState(loading, error: String?, ...)` as `StateFlow`; load in `init` + `refresh()`.
  Screens collect with `collectAsStateWithLifecycle()`.
- Every screen handles: loading (`LoadingState`), empty (`EmptyState`), error (`ErrorState(message, onRetry)`),
  destructive actions via `ConfirmDialog`. Min touch target 48dp. Keep tests/`unitTests.isReturnDefaultValues` in mind (no Log calls in repos).
- Inject `SupabaseClient` directly (`io.github.jan.supabase.SupabaseClient`) — provided by AppModule.
- DO NOT modify shared files: `Routes.kt` OK to read; do NOT edit `AppModule.kt`, theme, models, interfaces.
  Bind your repo impls in YOUR OWN Hilt module file, e.g. `data/di/WorkersModule.kt`:
  ```kotlin
  @Module @InstallIn(SingletonComponent::class)
  abstract class WorkersModule {
      @Binds abstract fun bindWorkerRepository(impl: WorkerRepositoryImpl): WorkerRepository
  }
  ```

## Database schema (authoritative column names)

All ids `uuid default gen_random_uuid()`; created_at `timestamptz default now()`. Time stored UTC.
- `profiles(id pk refs auth.users, full_name, email, role check in('admin','examiner','worker','management') default 'worker', account_status check in('pending','active','suspended') default 'pending', created_at, updated_at)`
- `departments(id, name unique, description, active_status bool default true, created_at)`
- `work_areas(id, department_id fk→departments, name, description, active_status, created_at, unique(department_id,name))`
- `shifts(id, name unique, start_time time, end_time time, timezone text default 'Asia/Jakarta', active_status, created_at)`
- `workers(id, employee_number unique, full_name, email, department_id fk, work_area_id fk→work_areas, job_title, shift_id fk→shifts, active_status default true, user_id unique refs auth.users null, created_at, updated_at)`
- `test_protocols(id, name, protocol_version, trial_count int>0, stimulus_delay_min_ms int, stimulus_delay_max_ms int, response_timeout_ms int, configuration jsonb default '{}', status check in('draft','active','retired') default 'draft', created_by refs profiles, approved_by refs profiles, created_at, updated_at, unique(name,protocol_version))`
- `fatigue_rules(id, protocol_id fk→test_protocols, rule_version, config jsonb, effective_from timestamptz default now(), effective_until, approval_status check in('draft','approved','retired') default 'draft', created_by, approved_by, created_at, unique(protocol_id,rule_version))`
- `test_sessions(id, worker_id fk→workers, examiner_id refs profiles, shift_id fk→shifts, protocol_id fk→test_protocols, fatigue_rule_id fk→fatigue_rules, app_version text, device_metadata jsonb default '{}', started_at, completed_at, session_status check in('created','in_progress','interrupted','pending_sync','finalized','failed') default 'created', interruption_reason text, created_at)` — indexes on worker_id, examiner_id, session_status, created_at desc
- `test_trials(id, session_id fk→test_sessions on delete cascade, trial_number int, stimulus_at_monotonic_ns bigint, tap_at_monotonic_ns bigint, reaction_time_ms int, trial_status check in('valid','false_start','missed','invalid'), false_start bool default false, missed_response bool default false, created_at, unique(session_id,trial_number))`
- `test_results(id, session_id fk→test_sessions unique, valid_trial_count, invalid_trial_count, missed_response_count, false_start_count, mean_reaction_time_ms numeric, median_reaction_time_ms numeric, min_reaction_time_ms int, max_reaction_time_ms int, standard_deviation_ms numeric, slow_response_count int, excluded_artifact_count int default 0, classification_code text, classification_label text, classification_explanation text, rule_id fk→fatigue_rules, rule_version text, created_at)`
- `follow_ups(id, session_id fk→test_sessions, action_type check in('recheck','rest','evaluation','escalation','other'), notes, status check in('open','in_progress','completed','cancelled') default 'open', assigned_to refs profiles, due_at, completed_at, created_at, updated_at)`
- `schedules(id, worker_id fk→workers, examiner_id refs profiles, shift_id fk→shifts, scheduled_at timestamptz not null, status check in('scheduled','in_progress','completed','cancelled','needs_repeat') default 'scheduled', notes, created_at, updated_at)`
- `audit_logs(id, actor_id refs profiles, action, entity_type, entity_id, occurred_at timestamptz default now(), metadata jsonb default '{}')` — append-only via triggers; RLS: admin read-only, no direct writes.
- Views: `v_session_overview` (security_invoker): session columns + worker_name, worker_number, examiner_name, protocol_name, protocol_version, classification_code, classification_label, severity, completed_at, mean_reaction_time_ms. Use it for history lists & reports.
- RPCs (SQL agent defines; server-authoritative):
  - `finalize_test_session(p_session_id uuid, p_trials jsonb, p_app_version text, p_device_metadata jsonb) returns jsonb` — recomputes metrics + classification from the approved rule, inserts trials + result, sets session finalized; idempotent; examiner-only (auth.uid() = examiner_id).
  - `dashboard_admin() returns jsonb`, `dashboard_management(p_from date, p_to date) returns jsonb`, `dashboard_examiner() returns jsonb` — shape matches `domain/model/Dashboard.kt` classes (camelCase keys → decode with `JsonCodec` into those models directly).

## Screen signatures (MUST match ui/navigation/RatigNavHost.kt)

```kotlin
// feature/splash/SplashScreen.kt
@Composable fun SplashRoute(onGoLogin: () -> Unit, onGoPending: () -> Unit, onGoHome: () -> Unit, onGoConfigError: () -> Unit)
@Composable fun ConfigErrorRoute(onRetry: () -> Unit)
// feature/auth/LoginScreen.kt
@Composable fun LoginRoute(onSignedIn: () -> Unit, onPending: () -> Unit)
@Composable fun PendingApprovalRoute(onApproved: () -> Unit, onSignOut: () -> Unit)
// feature/dashboard/DashboardScreen.kt
@Composable fun DashboardRoute(onOpenSession: (String) -> Unit, onOpenWorker: (String) -> Unit, onOpenAdmin: (String) -> Unit)
// feature/workers/WorkersScreen.kt
@Composable fun WorkersRoute(onOpenWorker: (String) -> Unit, onAddWorker: () -> Unit)
@Composable fun WorkerEditRoute(onDone: () -> Unit)   // reads workerId nav arg if present
@Composable fun WorkerDetailRoute(onBack: () -> Unit, onEdit: (String) -> Unit, onStartTest: (String) -> Unit, onOpenSession: (String) -> Unit)
// feature/testflow/*
@Composable fun TestInstructionsRoute(onReady: (String) -> Unit, onAborted: () -> Unit)
@Composable fun ReactionTestRoute(onFinished: (String) -> Unit, onAborted: () -> Unit)
@Composable fun TestResultRoute(onDone: () -> Unit)
// feature/history/*
@Composable fun HistoryRoute(onOpenSession: (String) -> Unit)
@Composable fun SessionDetailRoute(onBack: () -> Unit, onOpenWorker: (String) -> Unit)
// feature/followup/FollowUpScreen.kt
@Composable fun FollowUpsRoute(onOpenSession: (String) -> Unit)
// feature/schedules/*
@Composable fun SchedulesRoute(onOpenWorker: (String) -> Unit, onManageShifts: () -> Unit)
@Composable fun ShiftsRoute(onBack: () -> Unit)
// feature/reports/ReportsScreen.kt
@Composable fun ReportsRoute()
// feature/profile/ProfileScreen.kt
@Composable fun ProfileRoute(onSignOut: () -> Unit, onOpenAdmin: (String) -> Unit)
// feature/admin/*
@Composable fun UsersRoute(onBack: () -> Unit)
@Composable fun ProtocolsRoute(onBack: () -> Unit, onEdit: (String?) -> Unit)
@Composable fun ProtocolEditRoute(onDone: () -> Unit)  // reads protocolId nav arg if present
@Composable fun RulesRoute(onBack: () -> Unit)
@Composable fun AuditLogRoute(onBack: () -> Unit)
@Composable fun MasterDataRoute(onBack: () -> Unit)    // departments + work areas management
```

Screens with nav args read them via `savedStateHandle` in the VM (`SavedStateHandle.get<String>("workerId")`).
