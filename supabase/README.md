# RATIG — Supabase backend

PostgreSQL schema, Row Level Security, server-authoritative RPCs and four Deno
Edge Functions for the RATIG decision-support app.

```
supabase/
├── migrations/
│   ├── 0001_schema.sql             # extensions, tables, constraints, indexes, views
│   ├── 0002_security.sql           # RLS policies, auth trigger, RPCs, audit triggers
│   ├── 0003_operational_modes.sql  # workers.nik, test modes, mode metrics, finalize v2
│   ├── 0004_roles.sql              # 3-role model (super_admin/admin/user) + RPC gates
│   ├── 0005–0011_*.sql             # NIK trigger, view/report fixes, backfills,
│   │                               # mode classification, band alignment
│   ├── 0012_backfill_classification_labels.sql  # dashboard label cleanup
│   └── 0013_lock_admin_privileges.sql           # enforce 3-tier RLS on server
├── APPLY_ALL.sql              # ready-to-paste 0005–0013 (idempotent)
├── functions/
│   ├── approve-account/      # POST { user_id } — admin/super_admin
│   ├── admin-create-user/    # POST { email, full_name, role, ... } — admin/super_admin
│   ├── admin-create-worker/  # POST { employee_number, full_name, email?, password?, ... }
│   └── export-report/        # POST { from, to, department_id?, format: "csv" } — admin/super_admin
├── seed.sql                 # DEVELOPMENT-ONLY data — never run in production
├── config.toml
└── .env.example
```

## 1. Apply the migrations

**Option A — CLI (recommended):**

```bash
supabase link --project-ref <project-ref>
supabase db push          # applies supabase/migrations/* in order
```

**Option B — dashboard SQL editor:** open *SQL Editor* and paste, in order:
`0001_schema.sql`, `0002_security.sql`, `0003_operational_modes.sql`,
`0004_roles.sql`. For an existing project that already has 0001–0004, paste the
single `APPLY_ALL.sql` file (0005–0013, idempotent) instead of all nine files.
Order matters: security depends on the schema, `0003` redefines
`finalize_test_session` (v2), `0004` collapses roles to
super_admin/admin/user, and `0013` makes those roles authoritative in RLS.

Development data (optional): run `seed.sql` the same way, or `supabase db reset`
(which replays migrations + `seed.sql` locally).

## 2. Deploy the Edge Functions

```bash
supabase functions deploy approve-account
supabase functions deploy admin-create-user
supabase functions deploy admin-create-worker
supabase functions deploy export-report
```

For local testing:

```bash
cp supabase/.env.example supabase/.env    # fill in the values
supabase functions serve --env-file supabase/.env
```

All four functions expect `SUPABASE_URL` + `SUPABASE_SERVICE_ROLE_KEY`
(server-side only — never ship the service-role key in the APK) and a caller
`Authorization: Bearer <user jwt>` header. The app keeps its own copy of the
URL/anon key in `core/config/AppConfig.kt`.

## 3. Roles & the first super admin

RATIG has three roles:

| Role | App area |
|---|---|
| `super_admin` | Everything: user/role administration, master data, protocols, rules, monitoring |
| `admin` | Manage ordinary `user` accounts + monitoring (cannot change system/master data) |
| `user` | Run tests + view own history |

The standard login is **email/password**. Accounts are created by an
administrator; the optional Google button only appears when
`google.webClientId` is configured. Any new Auth user gets a `profiles` row via
the `on_auth_user_created` trigger with `role='user'`,
`account_status='pending'`. Promote the first super admin with SQL (dashboard
SQL editor):

```sql
UPDATE public.profiles
   SET role = 'super_admin', account_status = 'active'
 WHERE email = 'you@example.com';   -- replace
```

After that, onboard everyone from the app:

- **Pekerja → (+) Tambah Pekerja** has two paths. With a password, the app
  calls `admin-create-worker`, which creates the worker record **and** a
  `user` login atomically; password is required and ≥8 characters, duplicate
  NIP/email are rejected, and the Auth user is
  rolled back if worker creation fails. If the email field is blank, the app
  supplies `<employee_number>@ratig.local` as the login id, so no real mailbox
  or email hosting is needed. Without a password, only the worker record is
  created and that worker has no app login (the examiner runs tests on their
  behalf).
- **Profil → Pengguna → (+) Tambah Pengguna** uses `admin-create-user` for
  additional application accounts. A `super_admin` may create `admin`/`user`;
  an `admin` may create **only** `user` (enforced in both the Edge Function and
  RLS — migration 0013). A temporary password is **required**; the account is
  created `email_confirm: true` and no invitation email is sent. `email` is a
  unique login id and may be internal (for example `name@ratig.local`), so no
  email hosting is needed. Share the id and password directly.

Activating/suspending and role changes are audited automatically. Only a
`super_admin` may grant the `super_admin` role.

## 4. RPC surface (PostgREST, `supabase.postgrest.rpc`)

| Function | Args | Role | Returns |
|---|---|---|---|
| `finalize_test_session` | `p_session_id uuid, p_trials jsonb, p_app_version text, p_device_metadata jsonb` | session examiner | camelCase result incl. `metrics` (domain `TestResult` shape); idempotent — refinalize returns the stored result |
| `dashboard_admin` | — | admin | `AdminDashboardStats` shape |
| `dashboard_management` | `p_from date, p_to date` | management/admin | `ManagementDashboardStats` shape |
| `dashboard_examiner` | — | examiner | `ExaminerDashboardStats` shape |
| `reaction_trend` | `p_from date, p_to date, p_department_id text` | any signed-in user | array of `{date, meanMs, sampleCount}` |

All RPCs are `security definer`, `revoke`d from `anon`/`public`, `grant`ed to
`authenticated`. Errors are raised as PostgREST errors with codes such as
`NOT_AUTHENTICATED`, `FORBIDDEN`, `SESSION_NOT_FOUND`, `INVALID_STATE`,
`INVALID_TRIALS`, `INVALID_TRIAL at index N`, `INVALID_RANGE`.

## 5. RLS test scenarios

Deny-by-default: no policy matches → zero rows / permission error. Paste each
snippet into the SQL editor (each runs in a rolled-back transaction). Replace
the `<uuid>` placeholders with real ids from your database.

**Scenario 1 — worker sees only its own profile row (expect: exactly 1 row,
its own; `account_status`/`role` visible but not others').**

```sql
begin;
set local role authenticated;
set local request.jwt.claims = '{"sub": "<worker-auth-user-uuid>", "role": "authenticated", "email": "worker@example.com"}';

select id, email, role, account_status from public.profiles;
-- expect: 1 row (own). Other profiles are invisible.
rollback;
```

**Scenario 2 — worker cannot read other examiners' sessions (expect: only
sessions whose `workers.user_id` links to the caller).**

```sql
begin;
set local role authenticated;
set local request.jwt.claims = '{"sub": "<worker-auth-user-uuid>", "role": "authenticated"}';

select s.id, s.session_status
from public.test_sessions s;
-- expect: only sessions of the linked worker (0 rows if none).
rollback;
```

**Scenario 3 — worker/management cannot create or write sessions; only an
examiner can (expect: policy violation for the worker insert; success for the
examiner insert with own examiner_id).**

```sql
begin;
set local role authenticated;
set local request.jwt.claims = '{"sub": "<worker-auth-user-uuid>", "role": "authenticated"}';

insert into public.test_sessions (worker_id, protocol_id, examiner_id)
values ('<worker-uuid>', '<protocol-uuid>', '<worker-auth-user-uuid>');
-- expect: ERROR new row violates row-level security policy (worker role cannot insert).
rollback;

begin;
set local role authenticated;
set local request.jwt.claims = '{"sub": "<examiner-auth-user-uuid>", "role": "authenticated"}';

insert into public.test_sessions (worker_id, protocol_id, examiner_id)
values ('<worker-uuid>', '<protocol-uuid>', '<examiner-auth-user-uuid>');
-- expect: INSERT 0 1 (examiner inserts own session).
rollback;
```

**Scenario 4 — management sees only finalized sessions (expect: zero rows for
created/in_progress sessions, rows only for `finalized`).**

```sql
begin;
set local role authenticated;
set local request.jwt.claims = '{"sub": "<management-auth-user-uuid>", "role": "authenticated"}';

select id, session_status from public.test_sessions where session_status <> 'finalized';
-- expect: 0 rows (management is filtered to finalized only).

select id, session_status from public.test_sessions where session_status = 'finalized';
-- expect: all finalized sessions.
rollback;
```

**Scenario 5 — audit_logs are admin-only and immutable (expect: 0 rows for
worker, rows for admin; direct INSERT fails for everyone).**

```sql
begin;
set local role authenticated;
set local request.jwt.claims = '{"sub": "<worker-auth-user-uuid>", "role": "authenticated"}';

select count(*) from public.audit_logs;
-- expect: 0 rows.
rollback;

begin;
set local role authenticated;
set local request.jwt.claims = '{"sub": "<admin-auth-user-uuid>", "role": "authenticated"}';

select count(*) from public.audit_logs;   -- expect: > 0 once triggers have fired
insert into public.audit_logs (action, entity_type)
values ('HACK', 'profiles');
-- expect: ERROR — no INSERT policy (deny by default).
rollback;
```

Bonus check — finalized sessions are immutable outside the RPC (any role,
including the owning examiner):

```sql
begin;
set local role authenticated;
set local request.jwt.claims = '{"sub": "<examiner-auth-user-uuid>", "role": "authenticated"}';

update public.test_sessions set interruption_reason = 'tamper' where id = '<finalized-session-uuid>';
-- expect: ERROR FINALIZED_IMMUTABLE ...
rollback;
```

### Scenario 6 — admin cannot escalate privileges (migration 0013)

A plain `admin` may manage `user` rows, but must not write system/master data
or grant administrative roles. UI hiding alone is not access control; these
SQL checks verify the server contract. Replace the UUIDs with real ids.

```sql
begin;
set local role authenticated;
set local request.jwt.claims = '{"sub": "<admin-auth-user-uuid>", "role": "authenticated"}';

update public.fatigue_rules set rule_version = rule_version;
-- expect: 0 rows (RLS: write requires is_super_admin()).

update public.test_protocols set status = status;
-- expect: 0 rows.

update public.profiles set role = 'admin' where email = 'ordinary-user@example.com';
-- expect: 0 rows / permission denied (SQL client: 42501).

insert into public.departments (name) values ('HACK');
-- expect: ERROR new row violates row-level security policy.
rollback;

begin;
set local role authenticated;
set local request.jwt.claims = '{"sub": "<super-admin-auth-user-uuid>", "role": "authenticated"}';

update public.fatigue_rules set rule_version = rule_version;
-- expect: 1+ rows (super_admin is allowed).
rollback;
```

Edge Function check (with a real admin JWT): `POST /functions/v1/admin-create-user`
with `{"role":"admin", ...}` must return `403 Forbidden: hanya super admin
dapat membuat admin`; the same call with a super-admin JWT returns `201`.

## 6. Notes

- All views (`v_session_overview`, `v_follow_up_overview`, `v_schedule_overview`,
  `v_audit_overview`, `v_report_rows`) are `security_invoker = true`: queries
  through them are re-checked against the caller's RLS policies.
- `test_trials` / `test_results` / `audit_logs` accept writes only from the
  security-definer RPCs/triggers (no insert/update/delete policies).
- Metric + classification computation inside `finalize_test_session` mirrors
  `domain/usecase/MetricsCalculator.kt` and `FatigueClassifier.kt`
  (artifact exclusion, sample stddev, `INSUFFICIENT_DATA` / `NOT_CONFIGURED`,
  band lookup incl. `slow_count_band`).
- Day keys use `to_char(..., 'YYYY-MM-DD')` on
  `date_trunc('day', ts AT TIME ZONE 'Asia/Jakarta')`; numeric casts are
  explicit.

## 7. Test modes (0003)

Migration `0003_operational_modes.sql` adds three observation modes on top of
the classic reaction test. Every mode records raw per-trial data plus
observation rates; **only `classic` receives a fatigue band classification** —
the modes are never merged into one score.

| `test_sessions.test_mode` | Meaning | Observation metrics in `test_results` |
|---|---|---|
| `classic` | RGB reaction test (banded) | — (uses mean/median/slow-count bands) |
| `rgb_random` | Random RGB stimulus | `accuracy_rate`, `correct_response_rate`, `false_alarm_rate`, `omission_rate` |
| `random_button` | 3×3 target grid | same four rates |
| `focus_inhibition` | Go / No-Go inhibition | same four rates |

Reproducibility: `test_sessions.random_seed` stores the RNG seed and
`mode_config` freezes the protocol's mode configuration at session start, so a
running session never changes configuration mid-test and any randomization can
be replayed for audit.

Rate formulas (server-authoritative, mirrored by
`domain/usecase/ModeMetricsCalculator.kt`):

```
correct_response_rate = n_correct / n_target
false_alarm_rate      = n_response_to_distractor / n_distractor
omission_rate         = n_target_with_no_response / n_target
accuracy_rate         = (n_correct + n_distractor_ignored) / (n_target + n_distractor)
```

`finalize_test_session` v2 is idempotent and rejects explicit JSON `null`s in
trial fields via `jsonb_typeof` checks (see 0003 header).
