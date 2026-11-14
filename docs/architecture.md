# Architecture

RATIG is a single-module Android app (Kotlin + Jetpack Compose) backed by
Supabase (PostgreSQL + Auth + Edge Functions). It is **offline-first**: the
network is an enhancement, never a prerequisite for running a test.

## Layers

```
feature/            Compose screens + ViewModels (MVI-ish, one VM per screen)
  ├─ auth, dashboard, workers, testflow (+ testflow/modes),
  │  identification, sync, history, followup, reports, admin, profile
ui/
  ├─ navigation/    Routes.kt (typed route constants) + RatigNavHost.kt
  ├─ components/     shared Composable building blocks (States, Charts)
  └─ theme/          Material 3 color/type
domain/
  ├─ model/          pure Kotlin data classes (no Android, no Supabase types)
  ├─ repository/     repository INTERFACES consumed by ViewModels
  └─ usecase/        pure logic: MetricsCalculator, ModeMetricsCalculator,
                     FatigueClassifier
data/
  ├─ remote/         Supabase-backed repository IMPLEMENTATIONS + DTOs
  ├─ local/          Room database, entities, DAOs (offline cache)
  ├─ offline/        NetworkMonitor, OfflineGate, OfflinePolicyStore,
                     OfflineSessionManager
  ├─ sync/           SyncRepository(Impl)
  └─ di/             Hilt modules binding interface → implementation
core/
  ├─ config/         AppConfig (BuildConfig-sourced, public values only)
  ├─ identity/       NikValidator, NikMask, QrPayload
  ├─ timing/         MonotonicClock, ReactionTimeEngine, modes/* engines
  ├─ sync/           SyncWorker, SyncStatus, SyncModels, SyncStatusBus
  ├─ time/           TimeProvider (wall clock, injectable/testable)
  ├─ export/         CsvBuilder, PdfBuilder
  ├─ json/, result/  serialization + AppResult/AppError
  └─ device/         DeviceMetadata (model, OS, app version for audit)
```

**Dependency rule:** `feature → domain → (nothing)`. `domain` never imports
`data`, Android, or Supabase. `data` implements `domain` interfaces and is the
only layer that knows about PostgREST/Room.

## Navigation contract

All routes are declared in `ui/navigation/Routes.kt`; the graph is assembled in
`RatigNavHost.kt`. Screens receive navigation as lambdas (e.g.
`onStartIdentification`, `onOpenSyncStatus`, `onOpenWorkerEdit`) so feature
modules stay decoupled from the graph. Route constants carry the exact
parameter names shown in the file (e.g. `test_instructions/{sessionId}`).

## Timing (the product's core)

- Reaction timing uses `SystemClock.elapsedRealtimeNanos()` (monotonic,
  boot-relative). Wall-clock time is used **only** for audit timestamps, never
  to compute a reaction time.
- `ReactionTimeEngine` records the stimulus-render instant and the tap-dispatch
  instant, then computes `tapAt - stimulusAt` in ms.
- `core/timing/modes/*` holds the three observation-mode engines
  (`RgbRandomModeEngine`, `ButtonGridModeEngine`, `FocusInhibitionModeEngine`)
  behind `ModeEngine` / `ModeStimulus` / `ModeConfiguration`. Each engine is
  pure and unit-testable; the Compose layer only renders stimuli and forwards
  taps.

## Identity & privacy

- `nik` (TEXT) is the business key. It is validated (`NikValidator`), masked for
  display (`NikMask`), and parsed from QR payloads (`QrPayload`, prefix
  `RATIG1:` by default). NIK is never a foreign key and never logged in full.
- Foreign keys always use the worker's UUID (`workers.id`).
- PII collection is opt-in per protocol; see `domain/model/Worker.kt`.

## Offline-first

- `NetworkMonitor` exposes a `StateFlow<Boolean>` gated on
  `NET_CAPABILITY_VALIDATED` (captive portals count as offline).
- `OfflinePolicyStore` (DataStore) holds non-sensitive policy: offline-login
  validity (default 3 days), cache max age (default 72 h), NIK length bounds
  (16/16), QR prefix. Defaults live in `OfflinePolicy.DEFAULTS`.
- `OfflineSessionManager` + `OfflineGate` decide whether an action may proceed
  offline; cached rows older than `cacheMaxAgeMillis` are treated as stale.
- Room (`data/local`) is a **cache and outbox**, not the source of truth. The
  server remains authoritative; finalized results are immutable.

## Sync engine

- `SyncWorker` (WorkManager) inserts `test_sessions` with the client UUID, then
  calls the `finalize_test_session` RPC; a session is `SYNCED` only after the
  server confirms.
- Scheduling: unique **periodic** work every 3 h (`KEEP`) and a unique
  **one-time** work for "sync now" / reconnect (`REPLACE`), both constrained to
  `NetworkType.CONNECTED` with exponential backoff from 30 s.
- Failure classification lives in `SyncFailure.from()`: network/auth are
  retryable; RLS/validation/unknown are not and escalate to
  `NEEDS_REVIEW` / `FAILED_PERMANENT` after `SYNC_MAX_RETRIES`.
- `SyncStatusBus` + `SyncSnapshot` feed the dashboard card and the sync status
  screen. Count semantics are documented in `core/sync/SyncModels.kt`.

## Server-authoritative computation

Metrics and classification are computed twice: locally (for instant feedback,
`MetricsCalculator` / `ModeMetricsCalculator` / `FatigueClassifier`) and
server-side inside `finalize_test_session` (authoritative). Unit tests pin the
local formulas to the server formulas so they cannot drift. Only `classic` mode
receives a fatigue band; the three observation modes produce rate metrics and
are never merged into a single score.

## Configuration

`AppConfig` reads public values injected at build time from `local.properties`
(see `docs/deployment.md`). The service-role key never ships in the APK; all
authorization is enforced by PostgreSQL Row Level Security
(`supabase/migrations/0002_security.sql`).

---

## 13. Paramedic screening (skrining kesehatan)

The desktop RATIG application records blood pressure, pulse and breath alcohol
next to the reaction test, and derives a fit-to-work verdict. The Android app
mirrors that behaviour so the same worker is judged the same way on both
platforms.

**Where the data lives.** `local_health_screenings` is a Room table keyed by
`session_local_id` (one screening per session). It is deliberately device-local:
the values are health data and the server has no column for them yet. Storing
them locally keeps screening usable in a blank spot, matching the constraint the
reaction test already has.

**Thresholds.** `VitalsClassifier` reproduces the desktop `_auto_calc` chain
verbatim:

| Measurement | Bands (upper bound exclusive) |
|---|---|
| Systolic | <100 Hipotensi · <140 Normal · <160 Hipertensi Ringan · <180 Hipertensi Sedang · else Berat |
| Diastolic | <70 Hipotensi · <90 Normal · <100 Hipertensi Ringan · <120 Hipertensi Sedang · else Berat |
| Pulse | <60 Bradikardia · <100 Normal · <110 Takikardia Ringan · <120 Takikardia Sedang · else Berat |
| Alcohol | <0.08 % Lolos · else Tidak Lolos |

An absent measurement produces **no** finding. A missing value must never be
read as normal, so the classifier distinguishes "not measured" (`severity = -1`)
from "measured and normal" (`severity = 0`).

**Verdict.** `VitalsClassifier.recommend()` combines the screening with the
fatigue severity using worst-case: `FIT`, `FIT_WITH_NOTE`, or `NOT_FIT`. The
result screen shows the combined verdict and, when the reaction test raised it
above the screening-only verdict, says so explicitly. The verdict remains a
recommendation; the examiner decides.

**Migration.** Room version 2, `MIGRATION_1_2` adds the table. Verified on
device: `PRAGMA user_version = 2` and the table exists with all 12 columns.
