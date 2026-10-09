-- ============================================================================
-- RATIG — 0002_security.sql
-- RLS (deny-by-default, per-role policies), auth trigger, RPCs (security
-- definer, server-authoritative), audit + updated_at triggers.
-- Apply AFTER 0001_schema.sql.
-- ============================================================================

begin;

-- ===========================================================================
-- 1. Helper functions (SECURITY DEFINER so policies can read profiles
--    regardless of the caller's own RLS visibility)
-- ===========================================================================

create or replace function public.current_app_role()
returns text
language sql
stable
security definer
set search_path = public
as $$
    select role from public.profiles where id = auth.uid()
$$;

create or replace function public.current_account_status()
returns text
language sql
stable
security definer
set search_path = public
as $$
    select account_status from public.profiles where id = auth.uid()
$$;

create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.profiles
        where id = auth.uid() and role = 'admin'
    )
$$;

create or replace function public.is_examiner()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.profiles
        where id = auth.uid() and role = 'examiner'
    )
$$;

revoke execute on function public.current_app_role() from public, anon;
revoke execute on function public.current_account_status() from public, anon;
revoke execute on function public.is_admin() from public, anon;
revoke execute on function public.is_examiner() from public, anon;
grant execute on function public.current_app_role() to authenticated, service_role;
grant execute on function public.current_account_status() to authenticated, service_role;
grant execute on function public.is_admin() to authenticated, service_role;
grant execute on function public.is_examiner() to authenticated, service_role;

-- ===========================================================================
-- 2. New auth.users -> profiles trigger
-- ===========================================================================

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.profiles (id, full_name, email, role, account_status)
    values (
        new.id,
        coalesce(nullif(new.raw_user_meta_data ->> 'full_name', ''), split_part(new.email, '@', 1)),
        coalesce(new.email, ''),
        'worker',
        'pending'
    )
    on conflict (id) do nothing;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

-- ===========================================================================
-- 3. Row Level Security — enable on every table
-- ===========================================================================

alter table public.profiles       enable row level security;
alter table public.departments    enable row level security;
alter table public.work_areas     enable row level security;
alter table public.shifts         enable row level security;
alter table public.workers        enable row level security;
alter table public.test_protocols enable row level security;
alter table public.fatigue_rules  enable row level security;
alter table public.test_sessions  enable row level security;
alter table public.test_trials    enable row level security;
alter table public.test_results   enable row level security;
alter table public.follow_ups     enable row level security;
alter table public.schedules      enable row level security;
alter table public.audit_logs     enable row level security;

-- ---------------------------------------------------------------------------
-- profiles: read own; admin reads all; admin updates all except its own
-- role/status (self-demotion/self-approval is impossible).
-- ---------------------------------------------------------------------------

drop policy if exists profiles_select_own on public.profiles;
create policy profiles_select_own on public.profiles
    for select to authenticated
    using (id = auth.uid());

drop policy if exists profiles_select_admin on public.profiles;
create policy profiles_select_admin on public.profiles
    for select to authenticated
    using (is_admin());

drop policy if exists profiles_admin_update on public.profiles;
create policy profiles_admin_update on public.profiles
    for update to authenticated
    using (is_admin())
    with check (
        id <> auth.uid()
        or (
            role = (select p.role from public.profiles p where p.id = auth.uid())
            and account_status = (select p.account_status from public.profiles p where p.id = auth.uid())
        )
    );
-- No INSERT/DELETE policies on profiles: default deny (signup goes through the
-- auth.users trigger; account removal is a GoTrue/admin operation).

-- ---------------------------------------------------------------------------
-- departments / work_areas / shifts: readable by any signed-in user,
-- writable by admin only.
-- ---------------------------------------------------------------------------

drop policy if exists departments_select on public.departments;
create policy departments_select on public.departments
    for select to authenticated
    using (true);

drop policy if exists departments_admin_insert on public.departments;
create policy departments_admin_insert on public.departments
    for insert to authenticated
    with check (is_admin());

drop policy if exists departments_admin_update on public.departments;
create policy departments_admin_update on public.departments
    for update to authenticated
    using (is_admin())
    with check (is_admin());

drop policy if exists departments_admin_delete on public.departments;
create policy departments_admin_delete on public.departments
    for delete to authenticated
    using (is_admin());

drop policy if exists work_areas_select on public.work_areas;
create policy work_areas_select on public.work_areas
    for select to authenticated
    using (true);

drop policy if exists work_areas_admin_insert on public.work_areas;
create policy work_areas_admin_insert on public.work_areas
    for insert to authenticated
    with check (is_admin());

drop policy if exists work_areas_admin_update on public.work_areas;
create policy work_areas_admin_update on public.work_areas
    for update to authenticated
    using (is_admin())
    with check (is_admin());

drop policy if exists work_areas_admin_delete on public.work_areas;
create policy work_areas_admin_delete on public.work_areas
    for delete to authenticated
    using (is_admin());

drop policy if exists shifts_select on public.shifts;
create policy shifts_select on public.shifts
    for select to authenticated
    using (true);

drop policy if exists shifts_admin_insert on public.shifts;
create policy shifts_admin_insert on public.shifts
    for insert to authenticated
    with check (is_admin());

drop policy if exists shifts_admin_update on public.shifts;
create policy shifts_admin_update on public.shifts
    for update to authenticated
    using (is_admin())
    with check (is_admin());

drop policy if exists shifts_admin_delete on public.shifts;
create policy shifts_admin_delete on public.shifts
    for delete to authenticated
    using (is_admin());

-- ---------------------------------------------------------------------------
-- workers: admin/examiner read all; a linked worker reads its own row.
-- Writes: admin/examiner only (worker/management roles cannot write).
-- ---------------------------------------------------------------------------

drop policy if exists workers_select on public.workers;
create policy workers_select on public.workers
    for select to authenticated
    using (
        is_admin()
        or is_examiner()
        or user_id = auth.uid()
    );

drop policy if exists workers_insert on public.workers;
create policy workers_insert on public.workers
    for insert to authenticated
    with check (is_admin() or is_examiner());

drop policy if exists workers_update on public.workers;
create policy workers_update on public.workers
    for update to authenticated
    using (is_admin() or is_examiner())
    with check (is_admin() or is_examiner());

drop policy if exists workers_delete on public.workers;
create policy workers_delete on public.workers
    for delete to authenticated
    using (is_admin() or is_examiner());

-- ---------------------------------------------------------------------------
-- test_protocols: drafts visible to admin only; active/retired readable by
-- any signed-in user. Writes: admin only (approval flows through RPCs).
-- ---------------------------------------------------------------------------

drop policy if exists protocols_select on public.test_protocols;
create policy protocols_select on public.test_protocols
    for select to authenticated
    using (status <> 'draft' or is_admin());

drop policy if exists protocols_admin_insert on public.test_protocols;
create policy protocols_admin_insert on public.test_protocols
    for insert to authenticated
    with check (is_admin());

drop policy if exists protocols_admin_update on public.test_protocols;
create policy protocols_admin_update on public.test_protocols
    for update to authenticated
    using (is_admin())
    with check (is_admin());

drop policy if exists protocols_admin_delete on public.test_protocols;
create policy protocols_admin_delete on public.test_protocols
    for delete to authenticated
    using (is_admin());

-- ---------------------------------------------------------------------------
-- fatigue_rules: same draft pattern as protocols.
-- ---------------------------------------------------------------------------

drop policy if exists rules_select on public.fatigue_rules;
create policy rules_select on public.fatigue_rules
    for select to authenticated
    using (approval_status <> 'draft' or is_admin());

drop policy if exists rules_admin_insert on public.fatigue_rules;
create policy rules_admin_insert on public.fatigue_rules
    for insert to authenticated
    with check (is_admin());

drop policy if exists rules_admin_update on public.fatigue_rules;
create policy rules_admin_update on public.fatigue_rules
    for update to authenticated
    using (is_admin())
    with check (is_admin());

drop policy if exists rules_admin_delete on public.fatigue_rules;
create policy rules_admin_delete on public.fatigue_rules
    for delete to authenticated
    using (is_admin());

-- ---------------------------------------------------------------------------
-- test_sessions:
--   insert : examiner only, own examiner_id
--   select : admin | owning examiner | linked worker | management (finalized)
--   update : owning examiner while status in created/in_progress/interrupted
--            (finalized/failed rows are guarded by prevent_finalized_mutation)
--   delete : admin only (retention)
-- ---------------------------------------------------------------------------

drop policy if exists test_sessions_insert_examiner on public.test_sessions;
create policy test_sessions_insert_examiner on public.test_sessions
    for insert to authenticated
    with check (is_examiner() and examiner_id = auth.uid());

drop policy if exists test_sessions_select on public.test_sessions;
create policy test_sessions_select on public.test_sessions
    for select to authenticated
    using (
        is_admin()
        or examiner_id = auth.uid()
        or exists (
            select 1 from public.workers w
            where w.id = test_sessions.worker_id
              and w.user_id = auth.uid()
        )
        or (
            current_app_role() = 'management'
            and test_sessions.session_status = 'finalized'
        )
    );

drop policy if exists test_sessions_update_examiner on public.test_sessions;
create policy test_sessions_update_examiner on public.test_sessions
    for update to authenticated
    using (
        examiner_id = auth.uid()
        and test_sessions.session_status in ('created', 'in_progress', 'interrupted')
    )
    with check (examiner_id = auth.uid());

drop policy if exists test_sessions_admin_delete on public.test_sessions;
create policy test_sessions_admin_delete on public.test_sessions
    for delete to authenticated
    using (is_admin());

-- ---------------------------------------------------------------------------
-- test_trials / test_results: SELECT mirrors session visibility.
-- No INSERT/UPDATE/DELETE policies: clients write exclusively through the
-- security-definer finalize RPC, which bypasses RLS for its own inserts.
-- ---------------------------------------------------------------------------

drop policy if exists test_trials_select on public.test_trials;
create policy test_trials_select on public.test_trials
    for select to authenticated
    using (
        exists (
            select 1 from public.test_sessions s
            where s.id = test_trials.session_id
              and (
                    is_admin()
                    or s.examiner_id = auth.uid()
                    or exists (
                        select 1 from public.workers w
                        where w.id = s.worker_id
                          and w.user_id = auth.uid()
                    )
                    or (
                        current_app_role() = 'management'
                        and s.session_status = 'finalized'
                    )
              )
        )
    );

drop policy if exists test_results_select on public.test_results;
create policy test_results_select on public.test_results
    for select to authenticated
    using (
        exists (
            select 1 from public.test_sessions s
            where s.id = test_results.session_id
              and (
                    is_admin()
                    or s.examiner_id = auth.uid()
                    or exists (
                        select 1 from public.workers w
                        where w.id = s.worker_id
                          and w.user_id = auth.uid()
                    )
                    or (
                        current_app_role() = 'management'
                        and s.session_status = 'finalized'
                    )
              )
        )
    );

-- ---------------------------------------------------------------------------
-- follow_ups:
--   select : admin | examiner involved (assignee or session examiner) |
--            linked worker of the session
--   insert : examiner/admin
--   update : examiner/admin/assignee
--   delete : admin
-- ---------------------------------------------------------------------------

drop policy if exists follow_ups_select on public.follow_ups;
create policy follow_ups_select on public.follow_ups
    for select to authenticated
    using (
        is_admin()
        or follow_ups.assigned_to = auth.uid()
        or exists (
            select 1 from public.test_sessions s
            where s.id = follow_ups.session_id
              and (
                    s.examiner_id = auth.uid()
                    or exists (
                        select 1 from public.workers w
                        where w.id = s.worker_id
                          and w.user_id = auth.uid()
                    )
              )
        )
    );

drop policy if exists follow_ups_insert on public.follow_ups;
create policy follow_ups_insert on public.follow_ups
    for insert to authenticated
    with check (is_admin() or is_examiner());

drop policy if exists follow_ups_update on public.follow_ups;
create policy follow_ups_update on public.follow_ups
    for update to authenticated
    using (is_admin() or is_examiner() or follow_ups.assigned_to = auth.uid())
    with check (is_admin() or is_examiner() or follow_ups.assigned_to = auth.uid());

drop policy if exists follow_ups_admin_delete on public.follow_ups;
create policy follow_ups_admin_delete on public.follow_ups
    for delete to authenticated
    using (is_admin());

-- ---------------------------------------------------------------------------
-- schedules: admin/examiner manage; examiner sees own; linked worker sees own.
-- ---------------------------------------------------------------------------

drop policy if exists schedules_select on public.schedules;
create policy schedules_select on public.schedules
    for select to authenticated
    using (
        is_admin()
        or schedules.examiner_id = auth.uid()
        or exists (
            select 1 from public.workers w
            where w.id = schedules.worker_id
              and w.user_id = auth.uid()
        )
    );

drop policy if exists schedules_insert on public.schedules;
create policy schedules_insert on public.schedules
    for insert to authenticated
    with check (is_admin() or is_examiner());

drop policy if exists schedules_update on public.schedules;
create policy schedules_update on public.schedules
    for update to authenticated
    using (is_admin() or is_examiner())
    with check (is_admin() or is_examiner());

drop policy if exists schedules_delete on public.schedules;
create policy schedules_delete on public.schedules
    for delete to authenticated
    using (is_admin() or is_examiner());

-- ---------------------------------------------------------------------------
-- audit_logs: admin read-only. No INSERT/UPDATE/DELETE policies — writes
-- happen exclusively inside security-definer triggers.
-- ---------------------------------------------------------------------------

drop policy if exists audit_logs_admin_select on public.audit_logs;
create policy audit_logs_admin_select on public.audit_logs
    for select to authenticated
    using (is_admin());

-- ===========================================================================
-- 4. Guard: finalized sessions are immutable outside the finalize RPC.
--    The RPC sets transaction-local `app.rpc = on` so its own UPDATE passes.
-- ===========================================================================

create or replace function public.prevent_finalized_mutation()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if coalesce(current_setting('app.rpc', true), '') = 'on' then
        return new;
    end if;

    if old.session_status = 'finalized' or new.session_status = 'finalized' then
        raise exception
            'FINALIZED_IMMUTABLE: session % cannot be modified directly; use finalize_test_session rpc',
            old.id;
    end if;

    return new;
end;
$$;

drop trigger if exists test_sessions_guard_finalized on public.test_sessions;
create trigger test_sessions_guard_finalized
    before update on public.test_sessions
    for each row execute function public.prevent_finalized_mutation();

-- ===========================================================================
-- 5. Generic audit trigger (privacy: id + changed COLUMN NAMES only)
-- ===========================================================================

create or replace function public.audit_log()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    v_entity_id uuid;
    v_action    text;
    v_metadata  jsonb := '{}'::jsonb;
    v_old       jsonb;
    v_new       jsonb;
    v_changed   jsonb;
begin
    if tg_op = 'DELETE' then
        v_old := to_jsonb(old);
        v_entity_id := (v_old ->> 'id')::uuid;
        v_action := 'DELETE';
    else
        v_new := to_jsonb(new);
        v_entity_id := (v_new ->> 'id')::uuid;
        v_action := 'INSERT';

        if tg_op = 'UPDATE' then
            v_action := 'UPDATE';
            v_old := to_jsonb(old);

            select coalesce(jsonb_agg(k.key order by k.key), '[]'::jsonb)
            into v_changed
            from jsonb_object_keys(v_old) as k(key)
            where k.key <> 'updated_at'
              and (v_new -> k.key) is distinct from (v_old -> k.key);

            v_metadata := jsonb_build_object('changed', v_changed);

            -- Semantic action names for profile administration.
            if tg_table_name = 'profiles' then
                if v_new ->> 'role' is distinct from v_old ->> 'role' then
                    v_action := 'set_role';
                elsif v_new ->> 'account_status' is distinct from v_old ->> 'account_status' then
                    v_action := 'approve_account';
                end if;
            end if;
        end if;
    end if;

    insert into public.audit_logs (actor_id, action, entity_type, entity_id, metadata)
    values (auth.uid(), v_action, tg_table_name, v_entity_id, v_metadata);

    return coalesce(new, old);
end;
$$;

drop trigger if exists audit_profiles on public.profiles;
create trigger audit_profiles
    after insert or update or delete on public.profiles
    for each row execute function public.audit_log();

drop trigger if exists audit_workers on public.workers;
create trigger audit_workers
    after insert or update or delete on public.workers
    for each row execute function public.audit_log();

drop trigger if exists audit_test_protocols on public.test_protocols;
create trigger audit_test_protocols
    after insert or update or delete on public.test_protocols
    for each row execute function public.audit_log();

drop trigger if exists audit_fatigue_rules on public.fatigue_rules;
create trigger audit_fatigue_rules
    after insert or update or delete on public.fatigue_rules
    for each row execute function public.audit_log();

drop trigger if exists audit_follow_ups on public.follow_ups;
create trigger audit_follow_ups
    after insert or update or delete on public.follow_ups
    for each row execute function public.audit_log();

drop trigger if exists audit_schedules on public.schedules;
create trigger audit_schedules
    after insert or update or delete on public.schedules
    for each row execute function public.audit_log();

-- ===========================================================================
-- 6. updated_at maintenance
-- ===========================================================================

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

drop trigger if exists set_updated_at_profiles on public.profiles;
create trigger set_updated_at_profiles
    before update on public.profiles
    for each row execute function public.set_updated_at();

drop trigger if exists set_updated_at_workers on public.workers;
create trigger set_updated_at_workers
    before update on public.workers
    for each row execute function public.set_updated_at();

drop trigger if exists set_updated_at_protocols on public.test_protocols;
create trigger set_updated_at_protocols
    before update on public.test_protocols
    for each row execute function public.set_updated_at();

drop trigger if exists set_updated_at_follow_ups on public.follow_ups;
create trigger set_updated_at_follow_ups
    before update on public.follow_ups
    for each row execute function public.set_updated_at();

drop trigger if exists set_updated_at_schedules on public.schedules;
create trigger set_updated_at_schedules
    before update on public.schedules
    for each row execute function public.set_updated_at();

-- ===========================================================================
-- 7. RPC: finalize_test_session
-- Server-authoritative: validates examiner + state, wipes and re-inserts
-- trials, recomputes metrics EXACTLY like domain MetricsCalculator, applies
-- the effective approved rule exactly like domain FatigueClassifier,
-- upserts the result, finalizes the session. Idempotent.
-- ===========================================================================

create or replace function public.finalize_test_session(
    p_session_id      uuid,
    p_trials          jsonb,
    p_app_version     text default null,
    p_device_metadata jsonb default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_caller            uuid := auth.uid();
    v_session           public.test_sessions;
    v_rule              public.fatigue_rules;
    v_rule_config       jsonb;
    v_min_valid         integer;
    v_logic             text;
    v_slow_threshold    numeric;
    v_exclude_above     numeric;
    v_trial             jsonb;
    v_idx               integer := 0;
    v_trial_number      integer;
    v_trial_status      text;
    v_total_count       integer;
    v_valid_count       integer;   -- VALID trials INCLUDED in statistics
    v_excluded_count    integer;   -- VALID trials excluded as artifacts
    v_missed_count      integer;
    v_false_start_count integer;
    v_invalid_count     integer;
    v_min_ms            integer;
    v_max_ms            integer;
    v_mean              numeric;
    v_median            numeric;
    v_stddev            numeric;
    v_slow_count        integer;
    v_rule_found        boolean;
    v_metric_value      numeric;
    v_metric_trunc      integer;
    v_metric_name       text;
    v_band              jsonb;
    v_code              text;
    v_label             text;
    v_explanation       text;
    v_result            public.test_results;
begin
    if v_caller is null then
        raise exception 'NOT_AUTHENTICATED: sign in required';
    end if;

    if p_trials is null or jsonb_typeof(p_trials) <> 'array' then
        raise exception 'INVALID_TRIALS: p_trials must be a JSON array';
    end if;

    select * into v_session
    from public.test_sessions
    where id = p_session_id;

    if not found then
        raise exception 'SESSION_NOT_FOUND: no session %', p_session_id;
    end if;

    if v_session.examiner_id is distinct from v_caller then
        raise exception 'FORBIDDEN: only the examiner of this session may finalize it';
    end if;

    -- Idempotency: already finalized -> return the stored result untouched.
    if v_session.session_status = 'finalized' then
        select tr.* into v_result
        from public.test_results tr
        where tr.session_id = p_session_id;

        -- snake_case test_results row (JSON-null safe via to_jsonb).
        return coalesce(to_jsonb(v_result), '{}'::jsonb)
            || jsonb_build_object('session_id', p_session_id);
    end if;

    if v_session.session_status not in ('created', 'in_progress', 'interrupted') then
        raise exception 'INVALID_STATE: session status % cannot be finalized', v_session.session_status;
    end if;

    -- Allow our own UPDATE through prevent_finalized_mutation (tx-local).
    perform set_config('app.rpc', 'on', true);

    -- ------------------------------------------------------------------
    -- Validate + persist trials (idempotent: replace existing rows).
    -- ------------------------------------------------------------------
    delete from public.test_trials where test_trials.session_id = p_session_id;

    for v_trial in select * from jsonb_array_elements(p_trials) loop
        v_idx := v_idx + 1;

        if jsonb_typeof(v_trial) <> 'object' then
            raise exception 'INVALID_TRIAL at index %: each trial must be a JSON object', v_idx;
        end if;

        if coalesce(jsonb_typeof(v_trial -> 'trial_number'), '') <> 'number'
           or coalesce((v_trial ->> 'trial_number') ~ '^\d+$', false) = false
           or (v_trial ->> 'trial_number')::int < 1 then
            raise exception 'INVALID_TRIAL at index %: trial_number must be a positive integer', v_idx;
        end if;
        v_trial_number := (v_trial ->> 'trial_number')::int;

        v_trial_status := coalesce(v_trial ->> 'trial_status', '');
        if v_trial_status not in ('valid', 'false_start', 'missed', 'invalid') then
            raise exception 'INVALID_TRIAL at index %: trial_status must be valid|false_start|missed|invalid', v_idx;
        end if;

        if v_trial -> 'reaction_time_ms' is not null
           and (
                jsonb_typeof(v_trial -> 'reaction_time_ms') <> 'number'
                or (v_trial ->> 'reaction_time_ms') !~ '^\d+$'
           ) then
            raise exception 'INVALID_TRIAL at index %: reaction_time_ms must be a non-negative integer or null', v_idx;
        end if;

        if v_trial -> 'stimulus_at_monotonic_ns' is not null
           and (
                jsonb_typeof(v_trial -> 'stimulus_at_monotonic_ns') <> 'number'
                or (v_trial ->> 'stimulus_at_monotonic_ns') !~ '^-?\d+$'
           ) then
            raise exception 'INVALID_TRIAL at index %: stimulus_at_monotonic_ns must be an integer or null', v_idx;
        end if;

        if v_trial -> 'tap_at_monotonic_ns' is not null
           and (
                jsonb_typeof(v_trial -> 'tap_at_monotonic_ns') <> 'number'
                or (v_trial ->> 'tap_at_monotonic_ns') !~ '^-?\d+$'
           ) then
            raise exception 'INVALID_TRIAL at index %: tap_at_monotonic_ns must be an integer or null', v_idx;
        end if;

        if v_trial -> 'false_start' is not null
           and jsonb_typeof(v_trial -> 'false_start') <> 'boolean' then
            raise exception 'INVALID_TRIAL at index %: false_start must be boolean or null', v_idx;
        end if;

        if v_trial -> 'missed_response' is not null
           and jsonb_typeof(v_trial -> 'missed_response') <> 'boolean' then
            raise exception 'INVALID_TRIAL at index %: missed_response must be boolean or null', v_idx;
        end if;

        insert into public.test_trials (
            session_id,
            trial_number,
            stimulus_at_monotonic_ns,
            tap_at_monotonic_ns,
            reaction_time_ms,
            trial_status,
            false_start,
            missed_response
        ) values (
            p_session_id,
            v_trial_number,
            case when jsonb_typeof(v_trial -> 'stimulus_at_monotonic_ns') = 'number'
                 then (v_trial ->> 'stimulus_at_monotonic_ns')::bigint end,
            case when jsonb_typeof(v_trial -> 'tap_at_monotonic_ns') = 'number'
                 then (v_trial ->> 'tap_at_monotonic_ns')::bigint end,
            case when jsonb_typeof(v_trial -> 'reaction_time_ms') = 'number'
                 then (v_trial ->> 'reaction_time_ms')::int end,
            v_trial_status,
            coalesce((v_trial ->> 'false_start')::boolean, false),
            coalesce((v_trial ->> 'missed_response')::boolean, false)
        );
    end loop;

    -- ------------------------------------------------------------------
    -- Effective approved rule for this session's protocol.
    -- ------------------------------------------------------------------
    select * into v_rule
    from public.fatigue_rules fr
    where fr.protocol_id = v_session.protocol_id
      and fr.approval_status = 'approved'
      and fr.effective_from <= now()
      and coalesce(fr.effective_until, 'infinity'::timestamptz) > now()
    order by fr.effective_from desc
    limit 1;

    v_rule_found := found;

    if found then
        v_rule_config    := v_rule.config;
        v_min_valid      := coalesce((v_rule_config ->> 'minValidTrials')::int, 4);
        v_logic          := coalesce(v_rule_config ->> 'logic', 'mean_ms_band');
        v_slow_threshold := coalesce((v_rule_config ->> 'slowResponseThresholdMs')::numeric, 580);
        v_exclude_above  :=
            case
                when v_rule_config -> 'excludeReactionAboveMs' is null then null
                when jsonb_typeof(v_rule_config -> 'excludeReactionAboveMs') = 'null' then null
                else (v_rule_config ->> 'excludeReactionAboveMs')::numeric
            end;
    else
        v_min_valid      := 4;
        v_logic          := 'mean_ms_band';
        v_slow_threshold := 580;
        v_exclude_above  := null;
    end if;

    -- ------------------------------------------------------------------
    -- Counters (mirrors MetricsCalculator).
    -- ------------------------------------------------------------------
    select
        count(*)::int,
        count(*) filter (where trial_status = 'valid'
                          and reaction_time_ms is not null
                          and reaction_time_ms > v_exclude_above)::int,
        count(*) filter (where missed_response)::int,
        count(*) filter (where false_start)::int
    into v_total_count, v_excluded_count, v_missed_count, v_false_start_count
    from public.test_trials
    where test_trials.session_id = p_session_id;

    -- Statistics over INCLUDED valid reaction times only.
    select
        count(*)::int,
        min(rt)::int,
        max(rt)::int,
        avg(rt),
        percentile_cont(0.5) within group (order by rt),
        stddev_samp(rt),
        count(*) filter (where rt >= v_slow_threshold)::int
    into v_valid_count, v_min_ms, v_max_ms, v_mean, v_median, v_stddev, v_slow_count
    from (
        select t.reaction_time_ms as rt
        from public.test_trials t
        where t.session_id = p_session_id
          and t.trial_status = 'valid'
          and t.reaction_time_ms is not null
          and (v_exclude_above is null or t.reaction_time_ms <= v_exclude_above)
    ) included;

    v_invalid_count := v_total_count - v_valid_count;

    -- ------------------------------------------------------------------
    -- Classification (mirrors FatigueClassifier).
    -- ------------------------------------------------------------------
    v_metric_value :=
        case v_logic
            when 'median_ms_band' then v_median
            when 'slow_count_band' then v_slow_count
            else v_mean
        end;

    if not v_rule_found or v_valid_count < v_min_valid then
        v_code := 'INSUFFICIENT_DATA';
        v_label := 'Data tidak cukup';
        v_explanation := format(
            'Hanya %s percobaan valid (minimal %s). Klasifikasi tidak dapat dilakukan.',
            v_valid_count, v_min_valid);
    elsif v_metric_value is null then
        v_code := 'INSUFFICIENT_DATA';
        v_label := 'Data tidak cukup';
        v_explanation := format(
            'Metrik tidak tersedia untuk klasifikasi (minimal %s percobaan valid).', v_min_valid);
    elsif v_logic = 'slow_count_band' then
        select band into v_band
        from jsonb_array_elements(coalesce(v_rule_config -> 'slowCountBands', '[]'::jsonb)) band
        where (band ->> 'minSlowCount')::int <= v_slow_count
        order by (band ->> 'minSlowCount')::int desc
        limit 1;

        if v_band is null then
            v_code := 'NOT_CONFIGURED';
            v_label := 'Belum dikonfigurasi';
            v_explanation := 'Aturan klasifikasi belum memiliki rentang yang sesuai. ' ||
                'Periksa konfigurasi aturan pada modul Administrasi.';
        else
            v_code := v_band ->> 'code';
            v_label := v_band ->> 'label';
            v_explanation := format(
                'Klasifikasi berdasarkan jumlah respons lambat (%s, ambang %s ms): "%s".',
                v_slow_count, v_slow_threshold, v_band ->> 'label');
        end if;
    else
        -- mean_ms_band / median_ms_band: truncate like Kotlin Double.toLong().
        v_metric_trunc := floor(v_metric_value)::int;
        v_metric_name := case v_logic when 'median_ms_band' then 'median' else 'rata-rata (mean)' end;

        select band into v_band
        from jsonb_array_elements(coalesce(v_rule_config -> 'bands', '[]'::jsonb)) band
        where (band ->> 'minMs')::numeric <= v_metric_trunc
          and case
                when band -> 'maxMsExclusive' is null then true
                when jsonb_typeof(band -> 'maxMsExclusive') = 'null' then true
                else (band ->> 'maxMsExclusive')::numeric > v_metric_trunc
              end
        order by (band ->> 'minMs')::numeric
        limit 1;

        if v_band is null then
            v_code := 'NOT_CONFIGURED';
            v_label := 'Belum dikonfigurasi';
            v_explanation := 'Aturan klasifikasi belum memiliki rentang yang sesuai. ' ||
                'Periksa konfigurasi aturan pada modul Administrasi.';
        else
            v_code := v_band ->> 'code';
            v_label := v_band ->> 'label';
            v_explanation := format(
                'Klasifikasi berdasarkan %s reaksi %s ms ke dalam rentang %s–%s ms: "%s".',
                v_metric_name,
                round(v_metric_value, 1)::text,
                v_band ->> 'minMs',
                coalesce(nullif(v_band ->> 'maxMsExclusive', 'null'), '∞'),
                v_band ->> 'label');
        end if;
    end if;

    if not v_rule_found then
        -- No effective rule at all.
        v_code := 'NOT_CONFIGURED';
        v_label := 'Belum dikonfigurasi';
        v_explanation := 'Aturan klasifikasi efektif tidak ditemukan untuk protokol ini.';
    end if;

    -- ------------------------------------------------------------------
    -- Persist result (unique per session) + finalize session atomically.
    -- ------------------------------------------------------------------
    insert into public.test_results (
        session_id,
        valid_trial_count,
        invalid_trial_count,
        missed_response_count,
        false_start_count,
        mean_reaction_time_ms,
        median_reaction_time_ms,
        min_reaction_time_ms,
        max_reaction_time_ms,
        standard_deviation_ms,
        slow_response_count,
        excluded_artifact_count,
        classification_code,
        classification_label,
        classification_explanation,
        rule_id,
        rule_version
    ) values (
        p_session_id,
        v_valid_count,
        v_invalid_count,
        v_missed_count,
        v_false_start_count,
        v_mean,
        v_median,
        v_min_ms,
        v_max_ms,
        v_stddev,
        v_slow_count,
        v_excluded_count,
        v_code,
        v_label,
        v_explanation,
        v_rule.id,
        v_rule.rule_version
    )
    on conflict (session_id) do update set
        valid_trial_count          = excluded.valid_trial_count,
        invalid_trial_count        = excluded.invalid_trial_count,
        missed_response_count      = excluded.missed_response_count,
        false_start_count          = excluded.false_start_count,
        mean_reaction_time_ms      = excluded.mean_reaction_time_ms,
        median_reaction_time_ms    = excluded.median_reaction_time_ms,
        min_reaction_time_ms       = excluded.min_reaction_time_ms,
        max_reaction_time_ms       = excluded.max_reaction_time_ms,
        standard_deviation_ms      = excluded.standard_deviation_ms,
        slow_response_count        = excluded.slow_response_count,
        excluded_artifact_count    = excluded.excluded_artifact_count,
        classification_code        = excluded.classification_code,
        classification_label       = excluded.classification_label,
        classification_explanation = excluded.classification_explanation,
        rule_id                    = excluded.rule_id,
        rule_version               = excluded.rule_version,
        created_at                 = now();

    update public.test_sessions
    set session_status    = 'finalized',
        completed_at      = now(),
        app_version       = coalesce(p_app_version, app_version),
        device_metadata   = coalesce(p_device_metadata, device_metadata)
    where test_sessions.id = p_session_id;

    select * into v_result
    from public.test_results tr
    where tr.session_id = p_session_id;

    -- RAW test_results row (snake_case columns), not wrapped — the client
    -- decodes it directly into its DTO (ignoreUnknownKeys).
    return coalesce(to_jsonb(v_result), '{}'::jsonb)
        || jsonb_build_object('session_id', p_session_id);
end;
$$;

revoke execute on function public.finalize_test_session(uuid, jsonb, text, jsonb) from public, anon;
grant execute on function public.finalize_test_session(uuid, jsonb, text, jsonb) to authenticated, service_role;

-- ===========================================================================
-- 8. RPC: dashboards + trend (aggregate-only; camelCase domain shapes)
-- ===========================================================================

create or replace function public.dashboard_admin()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    v_today  date := (now() at time zone 'Asia/Jakarta')::date;
    v_result jsonb;
begin
    if not is_admin() then
        raise exception 'FORBIDDEN: admin only';
    end if;

    select jsonb_build_object(
        'activeWorkers',
            (select count(*) from public.workers where active_status),
        'totalUsers',
            (select count(*) from public.profiles),
        'pendingApprovals',
            (select count(*) from public.profiles where account_status = 'pending'),
        'totalSessions',
            (select count(*) from public.test_sessions),
        'sessionsToday',
            (select count(*) from public.test_sessions
              where (test_sessions.created_at at time zone 'Asia/Jakarta')::date = v_today),
        'unfinishedSessions',
            (select count(*) from public.test_sessions
              where test_sessions.session_status in ('created', 'in_progress', 'interrupted', 'pending_sync')),
        'openFollowUps',
            (select count(*) from public.follow_ups
              where public.follow_ups.status in ('open', 'in_progress')),
        'classificationCounts',
            coalesce((
                select jsonb_agg(jsonb_build_object(
                            'code', t.code,
                            'label', t.label,
                            'count', t.cnt,
                            'severity', t.severity) order by t.severity, t.code)
                from (
                    select
                        r.classification_code as code,
                        r.classification_label as label,
                        coalesce((
                            select (band ->> 'severity')::int
                            from public.fatigue_rules fr,
                                 jsonb_array_elements(fr.config -> 'bands') band
                            where fr.id = r.rule_id
                              and band ->> 'code' = r.classification_code
                            limit 1
                        ), 0) as severity,
                        count(*) as cnt
                    from public.test_results r
                    join public.test_sessions s on s.id = r.session_id
                    where s.completed_at >= now() - interval '90 days'
                      and r.classification_code is not null
                    group by r.classification_code, r.classification_label, r.rule_id
                ) t
            ), '[]'::jsonb),
        'byDepartment',
            coalesce((
                select jsonb_agg(jsonb_build_object('name', coalesce(t.name, 'Tanpa Departemen'),
                                                    'count', t.cnt) order by t.cnt desc, t.name)
                from (
                    select d.name as name, count(*) as cnt
                    from public.test_results r
                    join public.test_sessions s on s.id = r.session_id
                    join public.workers w on w.id = s.worker_id
                    left join public.departments d on d.id = w.department_id
                    where s.completed_at >= now() - interval '90 days'
                    group by d.name
                ) t
            ), '[]'::jsonb),
        'byShift',
            coalesce((
                select jsonb_agg(jsonb_build_object('name', coalesce(t.name, 'Tanpa Shift'),
                                                    'count', t.cnt) order by t.cnt desc, t.name)
                from (
                    select sh.name as name, count(*) as cnt
                    from public.test_results r
                    join public.test_sessions s on s.id = r.session_id
                    left join public.shifts sh on sh.id = s.shift_id
                    where s.completed_at >= now() - interval '90 days'
                    group by sh.name
                ) t
            ), '[]'::jsonb),
        'trend',
            coalesce((
                select jsonb_agg(jsonb_build_object('day', to_char(gs.day, 'YYYY-MM-DD'),
                                                    'count', coalesce(c.cnt, 0)) order by gs.day)
                from generate_series((v_today::timestamp - interval '13 days'), v_today::timestamp,
                                     interval '1 day') as gs(day)
                left join (
                    select (s.created_at at time zone 'Asia/Jakarta')::date as day, count(*) as cnt
                    from public.test_sessions s
                    where s.created_at >= ((v_today - 13) at time zone 'Asia/Jakarta')
                    group by 1
                ) c on c.day = gs.day::date
            ), '[]'::jsonb),
        'sampleSize',
            (select count(*)
             from public.test_results r
             join public.test_sessions s on s.id = r.session_id
             where s.completed_at >= now() - interval '90 days')
    ) into v_result;

    return v_result;
end;
$$;

revoke execute on function public.dashboard_admin() from public, anon;
grant execute on function public.dashboard_admin() to authenticated, service_role;

create or replace function public.dashboard_management(p_from date, p_to date)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    v_today  date := (now() at time zone 'Asia/Jakarta')::date;
    v_trend_to timestamp;
    v_result jsonb;
begin
    if current_app_role() not in ('admin', 'management') then
        raise exception 'FORBIDDEN: management or admin only';
    end if;
    if p_from is null or p_to is null or p_to < p_from then
        raise exception 'INVALID_RANGE: p_to must be on or after p_from';
    end if;

    -- Guard the trend series against unbounded ranges.
    v_trend_to := least(p_to, p_from + interval '366 days')::timestamp;

    select jsonb_build_object(
        'totalSessions',
            (select count(*) from public.test_sessions s
              where (s.created_at at time zone 'Asia/Jakarta')::date between p_from and p_to),
        'sessionsToday',
            (select count(*) from public.test_sessions s
              where (s.created_at at time zone 'Asia/Jakarta')::date = v_today),
        'openFollowUps',
            (select count(*) from public.follow_ups f
              where f.status in ('open', 'in_progress')),
        'classificationCounts',
            coalesce((
                select jsonb_agg(jsonb_build_object(
                            'code', t.code,
                            'label', t.label,
                            'count', t.cnt,
                            'severity', t.severity) order by t.severity, t.code)
                from (
                    select
                        r.classification_code as code,
                        r.classification_label as label,
                        coalesce((
                            select (band ->> 'severity')::int
                            from public.fatigue_rules fr,
                                 jsonb_array_elements(fr.config -> 'bands') band
                            where fr.id = r.rule_id
                              and band ->> 'code' = r.classification_code
                            limit 1
                        ), 0) as severity,
                        count(*) as cnt
                    from public.test_results r
                    join public.test_sessions s on s.id = r.session_id
                    where (s.created_at at time zone 'Asia/Jakarta')::date between p_from and p_to
                      and r.classification_code is not null
                    group by r.classification_code, r.classification_label, r.rule_id
                ) t
            ), '[]'::jsonb),
        'byDepartment',
            coalesce((
                select jsonb_agg(jsonb_build_object('name', coalesce(t.name, 'Tanpa Departemen'),
                                                    'count', t.cnt) order by t.cnt desc, t.name)
                from (
                    select d.name as name, count(*) as cnt
                    from public.test_results r
                    join public.test_sessions s on s.id = r.session_id
                    join public.workers w on w.id = s.worker_id
                    left join public.departments d on d.id = w.department_id
                    where (s.created_at at time zone 'Asia/Jakarta')::date between p_from and p_to
                    group by d.name
                ) t
            ), '[]'::jsonb),
        'byShift',
            coalesce((
                select jsonb_agg(jsonb_build_object('name', coalesce(t.name, 'Tanpa Shift'),
                                                    'count', t.cnt) order by t.cnt desc, t.name)
                from (
                    select sh.name as name, count(*) as cnt
                    from public.test_results r
                    join public.test_sessions s on s.id = r.session_id
                    left join public.shifts sh on sh.id = s.shift_id
                    where (s.created_at at time zone 'Asia/Jakarta')::date between p_from and p_to
                    group by sh.name
                ) t
            ), '[]'::jsonb),
        'trend',
            coalesce((
                select jsonb_agg(jsonb_build_object('day', to_char(gs.day, 'YYYY-MM-DD'),
                                                    'count', coalesce(c.cnt, 0)) order by gs.day)
                from generate_series(p_from::timestamp, v_trend_to, interval '1 day') as gs(day)
                left join (
                    select (s.created_at at time zone 'Asia/Jakarta')::date as day, count(*) as cnt
                    from public.test_sessions s
                    where s.created_at >= (p_from at time zone 'Asia/Jakarta')
                      and s.created_at < ((p_to + 1) at time zone 'Asia/Jakarta')
                    group by 1
                ) c on c.day = gs.day::date
            ), '[]'::jsonb),
        'sampleSize',
            (select count(*)
             from public.test_results r
             join public.test_sessions s on s.id = r.session_id
             where (s.created_at at time zone 'Asia/Jakarta')::date between p_from and p_to)
    ) into v_result;

    return v_result;
end;
$$;

revoke execute on function public.dashboard_management(date, date) from public, anon;
grant execute on function public.dashboard_management(date, date) to authenticated, service_role;

create or replace function public.dashboard_examiner()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    v_caller uuid := auth.uid();
    v_today  date := (now() at time zone 'Asia/Jakarta')::date;
    v_result jsonb;
begin
    if v_caller is null or current_app_role() <> 'examiner' then
        raise exception 'FORBIDDEN: examiner only';
    end if;

    select jsonb_build_object(
        'scheduledToday',
            (select count(*) from public.schedules sc
              where sc.examiner_id = v_caller
                and (sc.scheduled_at at time zone 'Asia/Jakarta')::date = v_today),
        'sessionsToday',
            (select count(*) from public.test_sessions s
              where s.examiner_id = v_caller
                and (s.created_at at time zone 'Asia/Jakarta')::date = v_today),
        'sessionsLast7Days',
            (select count(*) from public.test_sessions s
              where s.examiner_id = v_caller
                and s.created_at >= now() - interval '7 days'),
        'openFollowUps',
            (select count(*) from public.follow_ups f
              join public.test_sessions s on s.id = f.session_id
              where f.status in ('open', 'in_progress')
                and (f.assigned_to = v_caller or s.examiner_id = v_caller)),
        'recentResults',
            coalesce((
                select jsonb_agg(jsonb_build_object(
                            'sessionId', t.session_id,
                            'workerName', t.worker_name,
                            'completedAt', to_char(t.completed_at at time zone 'UTC',
                                                   'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'),
                            'classificationCode', t.classification_code,
                            'classificationLabel', t.classification_label,
                            'severity', coalesce((
                                select (band ->> 'severity')::int
                                from public.fatigue_rules fr,
                                     jsonb_array_elements(fr.config -> 'bands') band
                                where fr.id = t.rule_id
                                  and band ->> 'code' = t.classification_code
                                limit 1
                            ), 0),
                            'meanReactionTimeMs', t.mean_reaction_time_ms)
                    order by t.completed_at desc)
                from (
                    select s.id as session_id,
                           w.full_name as worker_name,
                           s.completed_at,
                           r.classification_code,
                           r.classification_label,
                           r.rule_id,
                           r.mean_reaction_time_ms
                    from public.test_results r
                    join public.test_sessions s on s.id = r.session_id
                    join public.workers w on w.id = s.worker_id
                    where s.examiner_id = v_caller
                      and s.session_status = 'finalized'
                    order by s.completed_at desc
                    limit 10
                ) t
            ), '[]'::jsonb)
    ) into v_result;

    return v_result;
end;
$$;

revoke execute on function public.dashboard_examiner() from public, anon;
grant execute on function public.dashboard_examiner() to authenticated, service_role;

create or replace function public.reaction_trend(
    p_from           date,
    p_to             date,
    p_department_id  text default null
)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    v_trend_to timestamp;
    v_result   jsonb;
begin
    if current_app_role() not in ('admin', 'management', 'examiner') then
        raise exception 'FORBIDDEN: management, admin or examiner only';
    end if;
    if p_from is null or p_to is null or p_to < p_from then
        raise exception 'INVALID_RANGE: p_to must be on or after p_from';
    end if;

    v_trend_to := least(p_to, p_from + interval '366 days')::timestamp;

    with daily as (
        select
            date_trunc('day', (rows_in_range.completed_at at time zone 'Asia/Jakarta')) as day,
            avg(rows_in_range.rt) as mean_ms,
            count(*) as cnt
        from (
            select s.completed_at, r.mean_reaction_time_ms as rt
            from public.test_results r
            join public.test_sessions s on s.id = r.session_id
            join public.workers w on w.id = s.worker_id
            where s.session_status = 'finalized'
              and s.completed_at is not null
              and (s.completed_at at time zone 'Asia/Jakarta')::date between p_from and v_trend_to::date
              and (
                    p_department_id is null
                    or w.department_id = p_department_id::uuid
              )
        ) rows_in_range
        group by 1
    )
    select coalesce(
        jsonb_agg(
            jsonb_build_object(
                'date', to_char(gs.day, 'YYYY-MM-DD'),
                'meanMs', d.mean_ms,
                'sampleCount', d.cnt
            ) order by gs.day
        ),
        '[]'::jsonb
    )
    into v_result
    from generate_series(p_from::timestamp, v_trend_to, interval '1 day') as gs(day)
    left join daily d on d.day = gs.day;

    return v_result;
end;
$$;

revoke execute on function public.reaction_trend(date, date, text) from public, anon;
grant execute on function public.reaction_trend(date, date, text) to authenticated, service_role;

commit;
