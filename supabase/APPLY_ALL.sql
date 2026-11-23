-- RATIG — APPLY ALL (paste entire file into Supabase SQL Editor)
-- Verified against schema 0001-0004. Idempotent; safe to re-run.


-- ===== 0005_worker_nik_trigger.sql =====
-- ============================================================================
-- RATIG — 0005_worker_nik_trigger.sql
-- ============================================================================
-- Automatically populates workers.nik when omitted on INSERT.
-- 0003 made workers.nik NOT NULL with CHECK (nik ~ '^[0-9]{4,32}$'), but client
-- INSERT bodies omitted nik expecting a server-side default/policy.
-- ============================================================================

CREATE OR REPLACE FUNCTION public.set_worker_default_nik()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.nik IS NULL OR trim(NEW.nik) = '' THEN
        IF NEW.employee_number ~ '^[0-9]{4,32}$' THEN
            NEW.nik := trim(NEW.employee_number);
        ELSE
            NEW.nik := '9' || lpad((abs(hashtext(NEW.employee_number)) % 100000000000000)::text, 15, '0');
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_worker_default_nik ON public.workers;
CREATE TRIGGER trg_worker_default_nik
BEFORE INSERT ON public.workers
FOR EACH ROW
EXECUTE FUNCTION public.set_worker_default_nik();


-- ===== 0006_fix_report_rows_view.sql =====
-- ============================================================================
-- RATIG — 0006_fix_report_rows_view.sql
-- ============================================================================
-- Adds worker_number and session_created_at aliases to v_report_rows to match
-- ReportRowDto serialization expectations.
-- Drops view first to allow column reordering.
-- ============================================================================

DROP VIEW IF EXISTS public.v_report_rows;

CREATE VIEW public.v_report_rows
WITH (security_invoker = true) AS
SELECT
    s.id         AS id,
    s.id         AS session_id,
    s.worker_id,
    w.employee_number,
    w.employee_number AS worker_number,
    w.full_name  AS worker_name,
    w.department_id AS department_id,
    d.name       AS department_name,
    wa.name      AS work_area_name,
    wa.name      AS area_name,
    sh.id        AS shift_id,
    sh.name      AS shift_name,
    s.started_at,
    s.completed_at,
    s.completed_at AS session_completed_at,
    s.created_at,
    s.created_at   AS session_created_at,
    examiner.full_name AS examiner_name,
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
        SELECT (band ->> 'severity')::int
        FROM public.fatigue_rules fr,
             jsonb_array_elements(fr.config -> 'bands') band
        WHERE fr.id = r.rule_id
          AND band ->> 'code' = r.classification_code
        LIMIT 1
    )            AS severity,
    r.rule_version,
    (
        SELECT fu.status
        FROM public.follow_ups fu
        WHERE fu.session_id = s.id
        ORDER BY fu.created_at DESC
        LIMIT 1
    )            AS follow_up_status,
    s.app_version
FROM public.test_sessions s
JOIN public.test_results r ON r.session_id = s.id
JOIN public.workers w ON w.id = s.worker_id
LEFT JOIN public.departments d ON d.id = w.department_id
LEFT JOIN public.work_areas wa ON wa.id = w.work_area_id
LEFT JOIN public.shifts sh ON sh.id = s.shift_id
LEFT JOIN public.profiles examiner ON examiner.id = s.examiner_id
WHERE s.session_status = 'finalized';


-- ===== 0007_user_self_access.sql =====
-- RATIG migration 0007 — ONE PERSON = ONE ACCOUNT, self-only access for User
--
-- Problem: 0004_roles.sql defined is_examiner() as "any active account".
-- That made every User (worker) an examiner in RLS terms, so workers_select
-- exposed the entire worker directory and workers_insert allowed a User to
-- register other people. Hiding the UI tab alone is not access control.
--
-- This migration:
--   1. Fixes is_examiner() to mean an actual staff role (admin/super_admin).
--   2. Keeps self-service working with explicit `user_id = auth.uid()` rules.
--   3. Adds a self-service session insert policy so a worker can test itself.
--   4. Tightens test_trials insert to the session owner.
--
-- Safe to re-run (idempotent).

-- ---------------------------------------------------------------------------
-- 1. Correct role predicates.
-- ---------------------------------------------------------------------------
create or replace function public.is_examiner()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.profiles
        where id = auth.uid()
          and account_status = 'active'
          and role in ('admin', 'super_admin')
    )
$$;

-- is_admin() already covers super_admin + admin in 0004_roles.sql. Keep it;
-- narrowing it to super_admin would break Admin policies (profiles, export).
-- It is restated here unchanged only so this migration is self-contained.
create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.profiles
        where id = auth.uid()
          and account_status = 'active'
          and role in ('super_admin', 'admin')
    )
$$;

-- Reusable self check.
create or replace function public.is_self_worker(p_worker_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.workers w
        where w.id = p_worker_id
          and w.user_id = auth.uid()
          and w.active_status = true
    )
$$;

-- ---------------------------------------------------------------------------
-- 2. Workers: a User sees only its own row, never the directory.
-- ---------------------------------------------------------------------------
drop policy if exists workers_select on public.workers;
create policy workers_select on public.workers
    for select to authenticated
    using (
        is_admin()
        or is_examiner()
        or user_id = auth.uid()
    );

-- Only staff may register people. The admin-create-worker edge function uses
-- the service role and is therefore unaffected by this policy.
drop policy if exists workers_insert on public.workers;
create policy workers_insert on public.workers
    for insert to authenticated
    with check (is_admin() or is_examiner());

drop policy if exists workers_update on public.workers;
create policy workers_update on public.workers
    for update to authenticated
    using (is_admin() or is_examiner())
    with check (is_admin() or is_examiner());

-- ---------------------------------------------------------------------------
-- 3. Sessions: staff as examiner, worker only for its own record.
-- ---------------------------------------------------------------------------
drop policy if exists test_sessions_insert_examiner on public.test_sessions;
create policy test_sessions_insert_examiner on public.test_sessions
    for insert to authenticated
    with check (
        (is_examiner() and examiner_id = auth.uid())
        or
        -- self-service: the worker is both subject and examiner of own test
        (is_self_worker(worker_id) and examiner_id = auth.uid())
    );

drop policy if exists test_sessions_select on public.test_sessions;
create policy test_sessions_select on public.test_sessions
    for select to authenticated
    using (
        is_admin()
        or is_examiner()
        or examiner_id = auth.uid()
        or is_self_worker(worker_id)
    );

-- ---------------------------------------------------------------------------
-- 4. Trials: only the session owner/staff may write.
-- ---------------------------------------------------------------------------
drop policy if exists test_trials_insert on public.test_trials;
create policy test_trials_insert on public.test_trials
    for insert to authenticated
    with check (
        exists (
            select 1 from public.test_sessions s
            where s.id = test_trials.session_id
              and (
                    is_examiner()
                    or s.examiner_id = auth.uid()
                    or is_self_worker(s.worker_id)
              )
        )
    );

-- ---------------------------------------------------------------------------
-- 5. Verification queries (run manually in SQL Editor):
--    select public.is_examiner();        -- false for a plain 'user' account
--    select count(*) from public.workers;-- 1 for a self-linked worker account
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- 6. Keep the examiner dashboard readable for self-service workers.
--    After is_examiner() is narrowed above, a plain User would otherwise be
--    rejected by dashboard_examiner() even for its OWN sessions. The JSON
--    shape stays identical to the original RPC - the app parses these keys.
-- ---------------------------------------------------------------------------
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
    v_staff  boolean;
begin
    if v_caller is null or (
        not is_examiner()
        and not exists (select 1 from public.workers w where w.user_id = v_caller)
    ) then
        raise exception 'FORBIDDEN: sign-in required';
    end if;

    v_staff := is_examiner();

    select jsonb_build_object(
        'scheduledToday',
            (select count(*) from public.schedules sc
              where (sc.examiner_id = v_caller
                     or sc.worker_id in (select id from public.workers where user_id = v_caller))
                and (sc.scheduled_at at time zone 'Asia/Jakarta')::date = v_today),
        'sessionsToday',
            (select count(*) from public.test_sessions s
              where (case when v_staff then s.examiner_id = v_caller
                          else s.worker_id in (select id from public.workers where user_id = v_caller) end)
                and (s.created_at at time zone 'Asia/Jakarta')::date = v_today),
        'sessionsLast7Days',
            (select count(*) from public.test_sessions s
              where (case when v_staff then s.examiner_id = v_caller
                          else s.worker_id in (select id from public.workers where user_id = v_caller) end)
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
                    where case when v_staff then s.examiner_id = v_caller
                               else s.worker_id in (select id from public.workers where user_id = v_caller) end
                    order by s.completed_at desc
                    limit 10
                ) t
            ), '[]'::jsonb)
    ) into v_result;
    return v_result;
end $$;


-- ===== 0008_backfill_session_shift.sql =====
-- RATIG migration 0008 — backfill session shift from actual test time
--
-- Problem: sessions used the examiner's dropdown choice or the worker profile
-- shift, so tests at 18:23 WIB were recorded as "Pagi" when the profile shift
-- was empty. From now on the app resolves the shift from completed_at in WIB
-- (ShiftResolver). This script repairs existing rows with the same rule.
--
-- Rules mirror ShiftResolver:
--   * normal range  [start, end)
--   * overnight     start >= end  -> time >= start OR time < end
--   * shift.timezone is honoured (default Asia/Jakarta)
--   * only rows whose current shift is NULL/missing are changed, so intentional
--     historical choices are not overwritten.
--
-- Run in Supabase SQL Editor. Idempotent.

do $$
declare
    updated integer := 0;
begin
    with resolved as (
        select s.id,
               (
                   select sh.id
                   from public.shifts sh
                   where sh.active_status = true
                     and (
                        case
                          when sh.start_time = sh.end_time then true
                          when sh.start_time < sh.end_time then
                              (s.completed_at at time zone coalesce(nullif(sh.timezone, ''), 'Asia/Jakarta'))::time
                                  >= sh.start_time
                              and
                              (s.completed_at at time zone coalesce(nullif(sh.timezone, ''), 'Asia/Jakarta'))::time
                                  < sh.end_time
                          else
                              (s.completed_at at time zone coalesce(nullif(sh.timezone, ''), 'Asia/Jakarta'))::time
                                  >= sh.start_time
                              or
                              (s.completed_at at time zone coalesce(nullif(sh.timezone, ''), 'Asia/Jakarta'))::time
                                  < sh.end_time
                        end
                     )
                   order by sh.start_time
                   limit 1
               ) as shift_id
        from public.test_sessions s
        -- FINALIZED rows are immutable (trigger prevent_finalized_mutation).
        -- They are corrected at read time by v_report_rows (migration 0011),
        -- so the backfill here only touches non-finalized sessions.
        where s.shift_id is null
          and s.completed_at is not null
          and s.session_status <> 'finalized'
    )
    update public.test_sessions s
       set shift_id = r.shift_id
      from resolved r
     where s.id = r.id
       and r.shift_id is not null;
    get diagnostics updated = row_count;
    raise notice 'Backfilled shift on % sessions', updated;
end $$;

-- Verification:
--   select s.id, s.completed_at, sh.name, sh.start_time, sh.end_time
--   from public.test_sessions s left join public.shifts sh on sh.id = s.shift_id
--   order by s.completed_at desc limit 20;


-- ===== 0010_align_fatigue_bands.sql =====
-- RATIG migration 0010 — align fatigue bands with the original desktop RATIG.py
--
-- Desktop rule (classify_fatigue):
--   avg < 240            -> Normal
--   240 <= avg <= 400    -> Fatigue Ringan
--   401 <= avg <= 580    -> Fatigue Sedang
--   avg >= 581           -> Fatigue Berat
--
-- The Android seed previously used exclusive upper bounds 240/400/580, which
-- put exactly 400 ms in "Sedang" and exactly 580 ms in "Berat". This migration
-- shifts the two upper bounds by 1 ms so both platforms classify identically.
--
-- Idempotent: only updates rows still carrying the old boundaries.

update public.fatigue_rules r
set config = jsonb_set(r.config, '{bands}', (
        select jsonb_agg(
            case
                when band ->> 'code' = 'BAND_2' then
                    jsonb_set(band, '{maxMsExclusive}', '401'::jsonb)
                when band ->> 'code' = 'BAND_3' then
                    jsonb_set(
                        jsonb_set(band, '{minMs}', '401'::jsonb),
                        '{maxMsExclusive}', '581'::jsonb)
                when band ->> 'code' = 'BAND_4' then
                    jsonb_set(band, '{minMs}', '581'::jsonb)
                else band
            end
        )
        from jsonb_array_elements(r.config -> 'bands') band
    ))
where r.config -> 'bands' @> '[{"code":"BAND_3","maxMsExclusive":580}]'::jsonb;

-- Verification:
--   select rule_version, config -> 'bands' from public.fatigue_rules;


-- ===== 0011_report_shift_fallback.sql =====
-- RATIG migration 0011 — derive shift in v_report_rows for legacy rows
--
-- Symptom: filter "Semua" shows all rows, "Pagi" shows 1 row, "Siang"/"Malam"
-- show nothing. Cause: legacy test_sessions.shift_id is NULL and the report
-- filter matches shift_id by equality.
--
-- This view is IDENTICAL to 0006_fix_report_rows_view.sql except that shift_id
-- and shift_name fall back to the configured shift containing completed_at.
-- All other columns, expressions and the finalized-only filter are preserved,
-- so ReportRowDto keeps decoding exactly as before.

drop view if exists public.v_report_rows;

create view public.v_report_rows
with (security_invoker = true) as
select
    s.id         as id,
    s.id         as session_id,
    s.worker_id,
    w.employee_number,
    w.employee_number as worker_number,
    w.full_name  as worker_name,
    w.department_id as department_id,
    d.name       as department_name,
    wa.name      as work_area_name,
    wa.name      as area_name,
    coalesce(s.shift_id, derived.id) as shift_id,
    coalesce(sh.name, derived.name)  as shift_name,
    s.started_at,
    s.completed_at,
    s.completed_at as session_completed_at,
    s.created_at,
    s.created_at   as session_created_at,
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
left join lateral (
    select sh2.id, sh2.name
    from public.shifts sh2
    where sh2.active_status = true
      and s.completed_at is not null
      and (
          case
            when sh2.start_time = sh2.end_time then true
            when sh2.start_time < sh2.end_time then
                (s.completed_at at time zone coalesce(nullif(sh2.timezone, ''), 'Asia/Jakarta'))::time
                    >= sh2.start_time
                and
                (s.completed_at at time zone coalesce(nullif(sh2.timezone, ''), 'Asia/Jakarta'))::time
                    <  sh2.end_time
            else
                (s.completed_at at time zone coalesce(nullif(sh2.timezone, ''), 'Asia/Jakarta'))::time
                    >= sh2.start_time
                or
                (s.completed_at at time zone coalesce(nullif(sh2.timezone, ''), 'Asia/Jakarta'))::time
                    <  sh2.end_time
          end
      )
    order by sh2.start_time
    limit 1
) derived on true
where s.session_status = 'finalized';

