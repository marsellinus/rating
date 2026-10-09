-- ============================================================================
-- RATIG — 0001_schema.sql
-- Full relational schema: extensions, tables, constraints, indexes, views.
-- Apply with `supabase db push` or the Supabase dashboard SQL editor.
-- ============================================================================

begin;

-- ---------------------------------------------------------------------------
-- Extensions
-- ---------------------------------------------------------------------------
create extension if not exists pgcrypto with schema extensions;

-- ---------------------------------------------------------------------------
-- profiles — mirrors auth.users 1:1, holds app role + approval state
-- ---------------------------------------------------------------------------
create table if not exists public.profiles (
    id             uuid primary key references auth.users (id) on delete cascade,
    full_name      text not null default '',
    email          text not null default '',
    role           text not null default 'worker'
                       constraint profiles_role_check
                       check (role in ('admin', 'examiner', 'worker', 'management')),
    account_status text not null default 'pending'
                       constraint profiles_account_status_check
                       check (account_status in ('pending', 'active', 'suspended')),
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- Organization master data
-- ---------------------------------------------------------------------------
create table if not exists public.departments (
    id            uuid primary key default gen_random_uuid(),
    name          text not null,
    description   text,
    active_status boolean not null default true,
    created_at    timestamptz not null default now(),
    constraint departments_name_key unique (name)
);

create table if not exists public.work_areas (
    id            uuid primary key default gen_random_uuid(),
    department_id uuid not null references public.departments (id) on delete cascade,
    name          text not null,
    description   text,
    active_status boolean not null default true,
    created_at    timestamptz not null default now(),
    constraint work_areas_department_id_name_key unique (department_id, name)
);

create table if not exists public.shifts (
    id            uuid primary key default gen_random_uuid(),
    name          text not null,
    start_time    time not null,
    end_time      time not null,
    timezone      text not null default 'Asia/Jakarta',
    active_status boolean not null default true,
    created_at    timestamptz not null default now(),
    constraint shifts_name_key unique (name)
);

-- ---------------------------------------------------------------------------
-- workers — examination subjects; user_id links a Google account (self view)
-- ---------------------------------------------------------------------------
create table if not exists public.workers (
    id              uuid primary key default gen_random_uuid(),
    employee_number text not null,
    full_name       text not null,
    email           text,
    department_id   uuid references public.departments (id) on delete set null,
    work_area_id    uuid references public.work_areas (id) on delete set null,
    job_title       text,
    shift_id        uuid references public.shifts (id) on delete set null,
    active_status   boolean not null default true,
    user_id         uuid unique references auth.users (id) on delete set null,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint workers_employee_number_key unique (employee_number)
);

-- ---------------------------------------------------------------------------
-- test_protocols — HOW the measurement is executed (versioned)
-- ---------------------------------------------------------------------------
create table if not exists public.test_protocols (
    id                     uuid primary key default gen_random_uuid(),
    name                   text not null,
    protocol_version       text not null,
    trial_count            integer not null constraint protocols_trial_count_check check (trial_count > 0),
    stimulus_delay_min_ms  integer not null
                               constraint protocols_delay_min_check check (stimulus_delay_min_ms >= 0),
    stimulus_delay_max_ms  integer not null
                               constraint protocols_delay_max_check check (stimulus_delay_max_ms >= stimulus_delay_min_ms),
    response_timeout_ms    integer not null constraint protocols_timeout_check check (response_timeout_ms > 0),
    configuration          jsonb not null default '{}'::jsonb,
    status                 text not null default 'draft'
                               constraint protocols_status_check check (status in ('draft', 'active', 'retired')),
    created_by             uuid references public.profiles (id) on delete set null,
    approved_by            uuid references public.profiles (id) on delete set null,
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),
    constraint test_protocols_name_protocol_version_key unique (name, protocol_version)
);

-- ---------------------------------------------------------------------------
-- fatigue_rules — classification bands + logic (versioned, approval-gated)
-- ---------------------------------------------------------------------------
create table if not exists public.fatigue_rules (
    id               uuid primary key default gen_random_uuid(),
    protocol_id      uuid not null references public.test_protocols (id) on delete cascade,
    rule_version     text not null,
    config           jsonb not null default '{}'::jsonb,
    effective_from   timestamptz not null default now(),
    effective_until  timestamptz,
    approval_status  text not null default 'draft'
                         constraint fatigue_rules_approval_status_check
                         check (approval_status in ('draft', 'approved', 'retired')),
    created_by       uuid references public.profiles (id) on delete set null,
    approved_by      uuid references public.profiles (id) on delete set null,
    created_at       timestamptz not null default now(),
    constraint fatigue_rules_protocol_id_rule_version_key unique (protocol_id, rule_version),
    constraint fatigue_rules_effective_window_check
        check (effective_until is null or effective_until > effective_from)
);

-- ---------------------------------------------------------------------------
-- test_sessions — one examination run
-- ---------------------------------------------------------------------------
create table if not exists public.test_sessions (
    id                  uuid primary key default gen_random_uuid(),
    worker_id           uuid not null references public.workers (id) on delete restrict,
    examiner_id         uuid references public.profiles (id) on delete set null,
    shift_id            uuid references public.shifts (id) on delete set null,
    protocol_id         uuid not null references public.test_protocols (id) on delete restrict,
    fatigue_rule_id     uuid references public.fatigue_rules (id) on delete set null,
    app_version         text,
    device_metadata     jsonb not null default '{}'::jsonb,
    started_at          timestamptz,
    completed_at        timestamptz,
    session_status      text not null default 'created'
                            constraint test_sessions_status_check
                            check (session_status in
                                ('created', 'in_progress', 'interrupted', 'pending_sync', 'finalized', 'failed')),
    interruption_reason text,
    created_at          timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- test_trials — raw per-trial measurements (written ONLY by finalize RPC)
-- ---------------------------------------------------------------------------
create table if not exists public.test_trials (
    id                        uuid primary key default gen_random_uuid(),
    session_id                uuid not null references public.test_sessions (id) on delete cascade,
    trial_number              integer not null constraint test_trials_number_check check (trial_number > 0),
    stimulus_at_monotonic_ns  bigint,
    tap_at_monotonic_ns       bigint,
    reaction_time_ms          integer constraint test_trials_rt_check check (reaction_time_ms is null or reaction_time_ms >= 0),
    trial_status              text not null
                                  constraint test_trials_status_check
                                  check (trial_status in ('valid', 'false_start', 'missed', 'invalid')),
    false_start               boolean not null default false,
    missed_response           boolean not null default false,
    created_at                timestamptz not null default now(),
    constraint test_trials_session_id_trial_number_key unique (session_id, trial_number)
);

-- ---------------------------------------------------------------------------
-- test_results — computed metrics + classification (one per session, RPC only)
-- ---------------------------------------------------------------------------
create table if not exists public.test_results (
    id                         uuid primary key default gen_random_uuid(),
    session_id                 uuid not null unique references public.test_sessions (id) on delete cascade,
    valid_trial_count          integer not null default 0,
    invalid_trial_count        integer not null default 0,
    missed_response_count      integer not null default 0,
    false_start_count          integer not null default 0,
    mean_reaction_time_ms      numeric,
    median_reaction_time_ms    numeric,
    min_reaction_time_ms       integer,
    max_reaction_time_ms       integer,
    standard_deviation_ms      numeric,
    slow_response_count        integer not null default 0,
    excluded_artifact_count    integer not null default 0,
    classification_code        text,
    classification_label       text,
    classification_explanation text,
    rule_id                    uuid references public.fatigue_rules (id) on delete set null,
    rule_version               text,
    created_at                 timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- follow_ups — recommended action after a result band
-- ---------------------------------------------------------------------------
create table if not exists public.follow_ups (
    id           uuid primary key default gen_random_uuid(),
    session_id   uuid not null references public.test_sessions (id) on delete cascade,
    action_type  text not null
                     constraint follow_ups_action_type_check
                     check (action_type in ('recheck', 'rest', 'evaluation', 'escalation', 'other')),
    notes        text,
    status       text not null default 'open'
                     constraint follow_ups_status_check
                     check (status in ('open', 'in_progress', 'completed', 'cancelled')),
    assigned_to  uuid references public.profiles (id) on delete set null,
    due_at       timestamptz,
    completed_at timestamptz,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- schedules — planned examination sessions
-- ---------------------------------------------------------------------------
create table if not exists public.schedules (
    id           uuid primary key default gen_random_uuid(),
    worker_id    uuid not null references public.workers (id) on delete cascade,
    examiner_id  uuid references public.profiles (id) on delete set null,
    shift_id     uuid references public.shifts (id) on delete set null,
    scheduled_at timestamptz not null,
    status       text not null default 'scheduled'
                     constraint schedules_status_check
                     check (status in ('scheduled', 'in_progress', 'completed', 'cancelled', 'needs_repeat')),
    notes        text,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- audit_logs — append-only trail written exclusively by triggers
-- ---------------------------------------------------------------------------
create table if not exists public.audit_logs (
    id          uuid primary key default gen_random_uuid(),
    actor_id    uuid references public.profiles (id) on delete set null,
    action      text not null,
    entity_type text not null,
    entity_id   uuid,
    occurred_at timestamptz not null default now(),
    metadata    jsonb not null default '{}'::jsonb
);

-- ---------------------------------------------------------------------------
-- Indexes
-- ---------------------------------------------------------------------------
create index if not exists idx_workers_department_id on public.workers (department_id);
create index if not exists idx_workers_shift_id on public.workers (shift_id);
create index if not exists idx_work_areas_department_id on public.work_areas (department_id);

create index if not exists idx_test_sessions_worker_id on public.test_sessions (worker_id);
create index if not exists idx_test_sessions_examiner_id on public.test_sessions (examiner_id);
create index if not exists idx_test_sessions_session_status on public.test_sessions (session_status);
create index if not exists idx_test_sessions_created_at on public.test_sessions (created_at desc);
create index if not exists idx_test_sessions_protocol_id on public.test_sessions (protocol_id);

create index if not exists idx_test_results_rule_id on public.test_results (rule_id);

create index if not exists idx_fatigue_rules_protocol_effective
    on public.fatigue_rules (protocol_id, effective_from desc);

create index if not exists idx_follow_ups_session_id on public.follow_ups (session_id);
create index if not exists idx_follow_ups_status on public.follow_ups (status);

create index if not exists idx_schedules_scheduled_at on public.schedules (scheduled_at);
create index if not exists idx_schedules_worker_id on public.schedules (worker_id);
create index if not exists idx_schedules_examiner_id on public.schedules (examiner_id);

create index if not exists idx_audit_logs_occurred_at on public.audit_logs (occurred_at desc);
create index if not exists idx_audit_logs_entity on public.audit_logs (entity_type, entity_id);
create index if not exists idx_audit_logs_action on public.audit_logs (action);

-- ---------------------------------------------------------------------------
-- Views (SECURITY INVOKER — RLS of the caller applies through the view)
-- ---------------------------------------------------------------------------

-- Session list/detail: session columns + denormalized display fields.
create or replace view public.v_session_overview
with (security_invoker = true) as
select
    s.id,
    s.worker_id,
    s.examiner_id,
    s.shift_id,
    s.protocol_id,
    s.fatigue_rule_id,
    s.app_version,
    s.device_metadata,
    s.started_at,
    s.completed_at,
    s.session_status,
    s.interruption_reason,
    s.created_at,
    w.full_name    as worker_name,
    w.employee_number as worker_number,
    w.department_id as department_id,
    examiner.full_name as examiner_name,
    p.name         as protocol_name,
    p.protocol_version,
    r.classification_code,
    r.classification_label,
    (
        select (band ->> 'severity')::int
        from public.fatigue_rules fr,
             jsonb_array_elements(fr.config -> 'bands') band
        where fr.id = r.rule_id
          and band ->> 'code' = r.classification_code
        limit 1
    )              as severity,
    r.mean_reaction_time_ms
from public.test_sessions s
left join public.workers w on w.id = s.worker_id
left join public.profiles examiner on examiner.id = s.examiner_id
left join public.test_protocols p on p.id = s.protocol_id
left join public.test_results r on r.session_id = s.id;

-- Follow-up list: follow-up columns + worker/examiner/assignee display fields.
create or replace view public.v_follow_up_overview
with (security_invoker = true) as
select
    f.*,
    s.worker_id,
    w.full_name    as worker_name,
    w.employee_number as worker_number,
    d.name         as department_name,
    assignee.full_name as assigned_to_name,
    examiner.full_name as session_examiner_name
from public.follow_ups f
join public.test_sessions s on s.id = f.session_id
left join public.workers w on w.id = s.worker_id
left join public.departments d on d.id = w.department_id
left join public.profiles assignee on assignee.id = f.assigned_to
left join public.profiles examiner on examiner.id = s.examiner_id;

-- Schedule list: schedule columns + worker/examiner/shift display fields.
create or replace view public.v_schedule_overview
with (security_invoker = true) as
select
    sc.*,
    w.full_name    as worker_name,
    w.employee_number as worker_number,
    examiner.full_name as examiner_name,
    sh.name        as shift_name,
    d.name         as department_name
from public.schedules sc
left join public.workers w on w.id = sc.worker_id
left join public.profiles examiner on examiner.id = sc.examiner_id
left join public.shifts sh on sh.id = sc.shift_id
left join public.departments d on d.id = w.department_id;

-- Audit trail: audit columns + actor display fields (admin-only via RLS).
create or replace view public.v_audit_overview
with (security_invoker = true) as
select
    a.*,
    p.full_name as actor_name,
    p.email     as actor_email
from public.audit_logs a
left join public.profiles p on p.id = a.actor_id;

-- Report rows: one row per finalized session with a computed result.
-- Superset shape: filters use department_id/shift_id (eq), completed_at
-- bounds; display/report uses the *_name columns and severity.
create or replace view public.v_report_rows
with (security_invoker = true) as
select
    s.id         as id,
    s.id         as session_id,
    s.worker_id,
    w.employee_number,
    w.full_name  as worker_name,
    w.department_id as department_id,
    d.name       as department_name,
    wa.name      as work_area_name,
    wa.name      as area_name,
    sh.id        as shift_id,
    sh.name      as shift_name,
    s.started_at,
    s.completed_at,
    s.completed_at as session_completed_at,
    s.created_at,
    examiner.full_name as examiner_name,
    r.valid_trial_count,
    r.invalid_trial_count,
    r.missed_response_count,
    r.false_start_count,
    r.mean_reaction_time_ms,
    r.median_reaction_time_ms,
    r.min_reaction_time_ms,
    r.max_reaction_time_ms,
    r.standard_deviation_ms,
    r.slow_response_count,
    r.excluded_artifact_count,
    r.classification_code,
    r.classification_label,
    r.classification_explanation,
    (
        select (band ->> 'severity')::int
        from public.fatigue_rules fr,
             jsonb_array_elements(fr.config -> 'bands') band
        where fr.id = r.rule_id
          and band ->> 'code' = r.classification_code
        limit 1
    )            as severity,
    r.rule_version,
    (
        select fu.status
        from public.follow_ups fu
        where fu.session_id = s.id
        order by fu.created_at desc
        limit 1
    )            as follow_up_status,
    s.app_version
from public.test_sessions s
join public.test_results r on r.session_id = s.id
join public.workers w on w.id = s.worker_id
left join public.departments d on d.id = w.department_id
left join public.work_areas wa on wa.id = w.work_area_id
left join public.shifts sh on sh.id = s.shift_id
left join public.profiles examiner on examiner.id = s.examiner_id
where s.session_status = 'finalized';

commit;
