# RATIG — Enhancement Contract (CONTRACT-2)

Applies ON TOP of CONTRACT.md (read both). Scope: test modes, offline-first (Room),
sync engine (WorkManager), worker identification by NIK. Base code (CONTRACT.md slice)
compiles green BEFORE you start — do not break it.

## Frozen domain changes (ALREADY APPLIED by integrator before you start — do NOT re-edit)
- `domain/model/Enums.kt`: + `enum class TestMode { CLASSIC, RGB_RANDOM, RANDOM_BUTTON, FOCUS_INHIBITION }` (SerialName snake_case, fromRaw like others).
- `domain/model/Testing.kt`:
  - `TestSession` + `testMode: TestMode = CLASSIC`, `randomSeed: String? = null`, `modeConfig: String? = null` (JSON string), `nikSnapshot: String? = null`, `syncStatus: SyncStatus = SYNCED` (local-only view field).
  - `TrialRecord` + `stimulusKind: String? = null`, `isTarget: Boolean? = null`, `responseType: String? = null` (`correct|wrong|late|early|none`), `responseCorrect: Boolean? = null`.
  - `TestResult` + `accuracyRate/correctResponseRate/falseAlarmRate/omissionRate: Double? = null`, `testMode: TestMode? = null`.
- `core/sync/SyncStatus.kt`: `enum class SyncStatus { PENDING, SYNCING, SYNCED, FAILED_RETRYABLE, FAILED_PERMANENT, NEEDS_REVIEW }`.
- `domain/repository/WorkerRepository.kt`: + `suspend fun findByNik(nik: String): AppResult<Worker?>`.
- `domain/repository/TestSessionRepository.kt`: `createSession(...)` gains `testMode: TestMode = CLASSIC, randomSeed: String? = null, modeConfig: String? = null, nikSnapshot: String? = null`.
- Gradle deps (integrator adds): Room (runtime/ktx/rxjava3? NO — plain + ktx + ksp compiler), WorkManager ktx, CameraX (camera-core/camera-camera2/camera-lifecycle/camera-view), ML Kit barcode-scanning 16.x, security-crypto (already), hilt-work + androidx.hilt compiler.

## Server contract (LIVE, already migrated & verified)
- `workers.nik TEXT UNIQUE NOT NULL CHECK (nik ~ '^[0-9]{4,32}$')`. NIK is a business key; sessions reference `workers.id` UUID only. NIK is never a number client-side.
- `test_sessions`: `test_mode`, `random_seed`, `mode_config jsonb`, `nik_snapshot`.
- `test_trials`: `stimulus_kind`, `is_target`, `response_type` ('correct'|'wrong'|'late'|'early'|'none'), `response_correct`.
- `test_results`: `accuracy_rate`, `correct_response_rate`, `false_alarm_rate`, `omission_rate`, `test_mode`.
- `finalize_test_session` v2 (idempotent): accepts the new trial fields; recomputes everything server-side; band classification ONLY for classic mode (others get `classification_* = null` + explanation); rate formulas (server-authoritative):
  - `correct_response_rate = n_correct / n_target`
  - `false_alarm_rate = n_response_to_distractor / n_distractor` (response_type in correct|wrong|late)
  - `omission_rate = n_target_with_no_response / n_target` (response_type='none' or missed)
  - `accuracy_rate = (n_correct + n_distractor_ignored) / (n_target + n_distractor)`

## Ownership map (do not edit files you don't own)
| Agent | Owns (creates) |
|---|---|
| TestModes | `core/timing/modes/*` (engines), `domain/usecase/ModeMetricsCalculator.kt`, `feature/testflow/modes/*` (UI), edits: `feature/testflow/TestInstructionsScreen.kt` (mode display + mode-specific instructions), `data/remote/TestSessionRepositoryImpl.kt` + `dto/TestDto.kt` (new fields), `feature/admin/ProtocolEditScreen.kt` + VM (mode config section), `feature/testflow/TestResultScreen.kt` (rates + mode display) |
| OfflineCore | `data/local/*` (Room DB, entities, DAOs, converters), `data/offline/*` (OfflineGate, OfflinePolicyStore, NetworkMonitor + impl), `data/session/OfflineSessionManager.kt` (offline login w/ cached profile), edits: `data/remote/WorkerRepositoryImpl.kt` (findByNik w/ cache-first), binds `data/di/OfflineModule.kt` |
| SyncEngine | `core/sync/*` (SyncWorker, SyncGateway, SyncStatusBus, SyncModels), `data/sync/SyncRepositoryImpl.kt`, binds in `data/di/SyncModule.kt`, edits: `feature/dashboard/DashboardScreen.kt` (sync status card), `ui/navigation/RatigNavHost.kt` ONLY the sync-status route registration given verbatim by integrator, `RatigApp.kt` (WorkManager init) |
| Identification | `feature/identification/*`, `core/identity/*` (NikValidator, NikMask, QrPayload), `data/remote/dto` none (uses repos), edits: `ui/navigation/Routes.kt` + `RatigNavHost.kt` ONLY the identification route registration given verbatim by integrator, `feature/dashboard/DashboardScreen.kt` examiner content ("Pemeriksaan Baru" button) |

Shared-file edits are SURGICAL (add a section, never restructure).

## Test modes (TestModes agent)
- Engines are pure Kotlin in `core/timing/modes/`, driven by injected `MonotonicClock` + `Random(seed)`; seed generated per session (`UUID.randomUUID().toString()` when none supplied) and SAVED — rerun with same seed reproduces the sequence (audit).
- NO re-randomization after a response is recorded; next stimulus only after previous trial resolves; double-response prevented (one response per trial; subsequent taps ignored).
- Modes:
  1. **RGB_RANDOM**: stimulus = one of red/green/blue square; target defined in instructions (e.g. "Tekan saat LINGKARAN HIJAU + ✓ muncul"); color+shape/symbol combined (never color-only: each color has a distinct shape overlay and symbol). Optional position randomization per `modeConfig.randomizePosition`.
  2. **RANDOM_BUTTON**: 3x3 grid of large buttons (min 64dp) inside safe area (exclude system bars); each trial places symbols on buttons using seeded random; 1 target symbol + k distractors per `modeConfig`; respond to target only; record RT + accuracy/false alarm/omission/false start.
  3. **FOCUS_INHIBITION**: go/no-go — green circle = go (tap), red circle = no-go (refrain), optional symbol rule from `modeConfig`; record go-correct, no-go-correct (rejection), commission errors (response to no-go), omissions. DO NOT label this "Stroop" or claim psychometric equivalence anywhere in UI/docs; call it "Fokus & Inhibisi" with the note "alat bantu pemantauan, bukan instrumen diagnostik tervalidasi".
- UI: progress indicator subtle (e.g. thin top bar or "x/y" small text); inter-trial rest per `modeConfig.restMs`; results screen shows per-mode metrics tiles + explicit note "Metrik observasi — bukan diagnosis".
- `ModeMetricsCalculator` (pure, unit-tested): mirrors server formulas exactly; input List<TrialRecord> + mode → per-mode metrics (mean/median/min/max/stdev over response_correct==true trials with reactionTimeMs in plausible range; rates per server formulas). Invalid trials NEVER enter RT stats.
- Trial recording: every attempt writes stimulusKind/isTarget/responseType/responseCorrect; response_type mapping: correct→correct; wrong response to target→wrong; response to distractor→wrong (false alarm); no response to target→none; response before stimulus→early (false_start=true); too-late (>timeout)→none+missed.

## Offline-first (OfflineCore agent)
- Room DB `ratig.db`, version 1, exportSchema false. Entities EXACTLY: `CachedProfileEntity`, `CachedWorkerEntity` (incl. `nik` indexed), `CachedProtocolEntity`, `LocalTestSessionEntity`, `LocalTestTrialEntity`, `LocalTestResultEntity`, `SyncQueueEntity`, `LocalAuditEventEntity`. Common columns: `local_id: String (UUID, PK, device-generated)`, `server_id: String?`, `owner_user_id: String`, `created_at`, `updated_at`, `sync_status: SyncStatus`, `retry_count: Int`, `last_sync_attempt_at: Long?`, `last_sync_error: String?` (sanitized), `protocol_version`, `payload_version: Int = 1`.
- Rule: NOTHING is `SYNCED` unless the server confirmed (finalize RPC 200 / select upsert success). Room save = PENDING ("Tersimpan di perangkat").
- `NetworkMonitor`: `interface { val isOnline: StateFlow<Boolean> }`, ConnectivityManager-based impl + Hilt binding.
- `OfflinePolicyStore` (DataStore): `offlineValidityDays` (default 3), `cacheMaxAgeHours` (default 72), `nikMinLength/nikMaxLength` (default 16/16), `qrPrefix` (default "RATIG1:").
- `OfflineGate.canAccessOffline(): OfflineDecision` — session cache exists AND profile active AND lastSync within validity → Allowed(role-scoped); else Denied(reason in Indonesian).
- Offline login: `OfflineSessionManager` — on app start with no network, restore from CachedProfileEntity; supersede NOTHING: Google login still requires network; cached identity only restores a previously server-verified session. Expired/offline-revoked → restricted mode (view-only cached results, no new exam for admin flows beyond examiner running tests IF gate allows).
- Privacy: Room DB in internal storage (default); NO worker/NIK data in SharedPreferences or logs; NIK masked via `NikMask` (e.g. 3201••••••••0099) everywhere except explicit confirm screen.

## Sync engine (SyncEngine agent)
- `SyncWorker` (CoroutineWorker, HiltWorker): constraint CONNECTED; backoff EXPONENTIAL, 30s; max retries per item 5 → FAILED_PERMANENT/NEEDS_REVIEW.
- Algorithm per PENDING session (oldest first, transactional in Room):
  1. Load session + trials + result from Room.
  2. Call `finalize_test_session` (idempotent; client UUID session: use `local_id` as p_session_id? NO — server generates UUID; instead create session row via REST insert with client-generated `id` (PostgREST allows specifying PK) then finalize with that id; duplicate re-send → REST insert conflicts on PK → treat 409 as already-exists → proceed to finalize which returns stored result (idempotent).
  3. On success: write server result row into LocalTestResultEntity, mark session/trials/result SYNCED + `server_id`, update SyncQueue.
  4. Error mapping: network → FAILED_RETRYABLE; 401 → FAILED_RETRYABLE (token refresh then requeue); 403/42501 → NEEDS_REVIEW ("Akses ditolak server"); 400 validation → NEEDS_REVIEW with sanitized message; 409 → idempotent path.
- `SyncStatusBus`: `@Singleton` StateFlow<`SyncSnapshot(isOnline, pendingSessions, pendingTrials, failedCount, lastSyncAt: Instant?, lastSyncError: String?`)>; updated by worker + DAO watchers (Flow).
- Manual sync button + "Sync sekarang" via OneTimeWorkRequest; observe WorkInfo to show SYNCING.
- NEVER delete local rows on success (retention); NEVER mark synced without confirmation; NEVER log tokens/NIK.

## Identification (Identification agent)
- Route `IDENTIFICATION = "identification"` (registered by integrator — see verbatim block below).
- `IdentificationRoute(onBack, onSessionReady: (sessionId: String) -> Unit)`: big buttons "Input NIK" / "Scan Barcode" / "Scan QR"; online/offline chip (NetworkMonitor); today's exam count (local+server); unsynced count.
- NIK input: `KeyboardOptions(keyboardType = KeyboardType.NumberPassword)` but stored/processed ONLY as String; live validation via `NikValidator` (digits only, length per OfflinePolicyStore, leading zeros preserved); debounce 350ms server search ONLY when online AND length valid; offline → Room search by nik prefix/exact.
- Found → autofill card (masked NIK, name, department, area, title, shift, status, last exam summary if available) + source badge ("Live server" / "Cache — diperbarui HH:mm"); explicit "Konfirmasi Pekerja" before anything; inactive worker → warning + block test (per policy); not found → "Pekerja tidak ditemukan" + search again + (examiner) "Daftarkan Pekerja" which navigates to existing WorkerEditRoute (NO auto-create from scan ever).
- Scanner: CameraX Preview + ML Kit barcode analyzer (formats: QR_CODE + CODE_128 default; documented + configurable); camera permission requested only when opening scanner; denied/unavailable → clear message + manual input still works; duplicate detections ignored via debounce + `hasValue` guard; QR payload format `RATIG1:<NIK>` — anything else → "QR tidak dikenali" (never auto-treat scan as authentication).
- Multi-worker flow: after session finished/results shown → "Kembali ke Identifikasi" clears worker data from the screen (history remains); switching worker with active unsaved session → ConfirmDialog.
- Session creation: IdentificationViewModel calls `TestSessionRepository.createSession(..., nikSnapshot = maskedOrFullPerPolicy)`; then `onSessionReady(sessionId)` → navigates to existing TEST_INSTRUCTIONS.

## Verbatim route registration (integrator applies — agents do NOT edit Routes/NavHost)
```kotlin
const val IDENTIFICATION = "identification"
const val SYNC_STATUS = "sync_status"
```
```kotlin
composable(Routes.IDENTIFICATION) {
    com.ratig.app.feature.identification.IdentificationRoute(
        onBack = { navController.popBackStack() },
        onSessionReady = { navController.navigate(Routes.testInstructions(it)) },
    )
}
composable(Routes.SYNC_STATUS) {
    com.ratig.app.feature.sync.SyncStatusRoute(
        onBack = { navController.popBackStack() },
    )
}
```
(SyncStatusRoute lives in `feature/sync/` — OWNED BY SyncEngine; IdentificationRoute in `feature/identification/`.)
Examiner DashboardContent gains a big "Pemeriksaan Baru" button: `onOpenAdmin(Routes.IDENTIFICATION)` is WRONG (not admin) — instead DashboardRoute signature GAINS `onStartIdentification: () -> Unit` (integrator applies). Identification feature must NOT edit DashboardScreen.kt — integrator wires the button.
