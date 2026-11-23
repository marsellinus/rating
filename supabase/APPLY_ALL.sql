-- ============================================================
-- RATIG — APPLY ALL (paste this entire file into Supabase SQL Editor)
-- Order: 0005 -> 0011. Every statement is idempotent.
-- ============================================================


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
begin
    if v_caller is null or (
        not is_examiner()
        and not exists (select 1 from public.workers w where w.user_id = v_caller)
    ) then
        raise exception 'FORBIDDEN: sign-in required';
    end if;

    select jsonb_build_object(
        'scheduledToday',
            (select count(*) from public.schedules sc
              where (sc.examiner_id = v_caller
                     or sc.worker_id in (select id from public.workers where user_id = v_caller))
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


-- ===== 0009_mixed_mode_classification.sql =====
-- RATIG migration 0009 — mixed-mode session classification
--
-- With the "one button runs all four modes" flow, one test_sessions row now
-- holds trials from Classic + RGB + Grid + Focus. The server must compute the
-- fatigue classification from the CLASSIC trials only (is_target IS NULL),
-- exactly as the rule states. The per-mode accuracy rates already use only
-- rows WHERE is_target IS NOT NULL, so no change is needed there.
--
-- Before this migration the mean/median were computed over ALL valid trials,
-- which mixed incompatible tasks and corrupted the fatigue band.
--
-- Run in Supabase SQL Editor. Replaces finalize_test_session's statistics
-- subquery; all other behaviour is preserved.

create or replace function public.finalize_test_session(
    p_session_id uuid,
    p_trials jsonb,
    p_app_version text default null,
    p_device_metadata jsonb default null
)
returns public.test_results
language plpgsql
security definer
set search_path = public
as $$
declare
    v_session public.test_sessions;
    v_rule_id uuid;
    v_config jsonb;
    v_bands jsonb;
    v_band jsonb;
    v_logic text;
    v_min_valid int;
    v_slow_threshold numeric;
    v_exclude_above numeric;
    v_total_count int;
    v_excluded_count int;
    v_missed_count int;
    v_false_start_count int;
    v_valid_count int;
    v_min_ms int;
    v_max_ms int;
    v_mean numeric;
    v_median numeric;
    v_stddev numeric;
    v_slow_count int;
    v_metric_value numeric;
    v_code text;
    v_label text;
    v_explanation text;
    v_rule_found boolean := false;
    v_has_classic boolean;
    v_result public.test_results;
begin
    select * into v_session from public.test_sessions where id = p_session_id;
    if not found then
        raise exception 'Session not found';
    end if;

    -- Insert trials (idempotent on (session_id, trial_number)).
    insert into public.test_trials (
        session_id, trial_number, stimulus_kind, is_target, response_type,
        response_correct, reaction_time_ms, trial_status, false_start,
        missed_response, stimulus_at_monotonic_ns, tap_at_monotonic_ns
    )
    select
        p_session_id,
        (t ->> 'trialNumber')::int,
        nullif(t ->> 'stimulusKind', ''),
        case when t ? 'isTarget' then (t ->> 'isTarget')::boolean else null end,
        nullif(t ->> 'responseType', ''),
        case when t ? 'responseCorrect' then (t ->> 'responseCorrect')::boolean else null end,
        case when t ? 'reactionTimeMs' then (t ->> 'reactionTimeMs')::numeric end,
        coalesce(nullif(t ->> 'trialStatus', ''), 'invalid'),
        coalesce((t ->> 'falseStart')::boolean, false),
        coalesce((t ->> 'missedResponse')::boolean, false),
        case when t ? 'stimulusAtMonotonicNs' then (t ->> 'stimulusAtMonotonicNs')::bigint end,
        case when t ? 'tapAtMonotonicNs' then (t ->> 'tapAtMonotonicNs')::bigint end
    from jsonb_array_elements(p_trials) as t
    on conflict (session_id, trial_number) do nothing;

    select count(*) > 0 into v_has_classic
    from public.test_trials
    where session_id = p_session_id and is_target is null;

    -- Effective rule (unchanged logic, abbreviated by reusing existing helper).
    select r.id, r.config into v_rule_id, v_config
    from public.fatigue_rules r
    where r.protocol_id = v_session.protocol_id
      and r.approval_status = 'approved'
      and r.effective_from <= now()
      and (r.effective_until is null or r.effective_until > now())
    order by r.effective_from desc
    limit 1;

    if v_rule_id is null then
        select r.id, r.config into v_rule_id, v_config
        from public.fatigue_rules r
        where r.protocol_id = v_session.protocol_id
        order by r.effective_from desc
        limit 1;
    end if;

    select count(*),
           count(*) filter (where trial_status = 'valid' and reaction_time_ms is not null
                              and reaction_time_ms > coalesce(v_exclude_above, 999999999)),
           count(*) filter (where missed_response),
           count(*) filter (where false_start)
    into v_total_count, v_excluded_count, v_missed_count, v_false_start_count
    from public.test_trials where session_id = p_session_id;

    if v_config is not null then
        v_logic := coalesce(v_config ->> 'logic', 'mean_ms_band');
        v_min_valid := coalesce((v_config ->> 'minValidTrials')::int, 4);
        v_slow_threshold := coalesce((v_config ->> 'slowResponseThresholdMs')::numeric, 580);
        v_exclude_above := nullif(v_config ->> 'excludeReactionAboveMs', '')::numeric;
        v_bands := v_config -> 'bands';
    end if;

    -- Statistics: CLASSIC trials only when the session contains them. Pure
    -- observation modes have no classic trials, so fall back to all valid rows.
    select count(*)::int, min(rt)::int, max(rt)::int, avg(rt),
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
          and (not v_has_classic or t.is_target is null)
    ) included;

    v_excluded_count := v_total_count - coalesce(v_valid_count, 0);

    -- Classification remains gated to the classic band rules.
    if coalesce(v_session.test_mode, 'classic') = 'classic' and v_bands is not null then
        if coalesce(v_valid_count, 0) < coalesce(v_min_valid, 4) then
            v_code := 'INSUFFICIENT_DATA';
            v_label := 'Data tidak cukup';
            v_explanation := 'Percobaan klasik valid kurang dari minimum aturan.';
        else
            v_metric_value := case v_logic
                when 'median_ms_band' then v_median
                when 'slow_count_band' then v_slow_count::numeric
                else v_mean
            end;
            for v_band in select value from jsonb_array_elements(v_bands) loop
                if v_logic = 'slow_count_band' then
                    if v_metric_value >= (v_band ->> 'minSlowCount')::numeric then
                        v_code := v_band ->> 'code'; v_label := v_band ->> 'label';
                        v_rule_found := true; exit;
                    end if;
                elsif v_metric_value >= (v_band ->> 'minMs')::numeric
                  and (v_band ->> 'maxMsExclusive') is null
                      or (v_metric_value >= (v_band ->> 'minMs')::numeric
                          and v_metric_value < (v_band ->> 'maxMsExclusive')::numeric) then
                    v_code := v_band ->> 'code'; v_label := v_band ->> 'label';
                    v_rule_found := true; exit;
                end if;
            end loop;
            if not v_rule_found then
                v_code := 'NOT_CONFIGURED'; v_label := 'Belum dikonfigurasi';
                v_explanation := 'Aturan klasifikasi tidak memiliki rentang yang sesuai.';
            end if;
        end if;
    else
        v_code := null; v_label := null;
        v_explanation := 'Metrik observasi mode ' || coalesce(v_session.test_mode, 'classic') ||
                         '. Klasifikasi kelelahan hanya diterapkan pada mode reaksi klasik.';
    end if;

    insert into public.test_results (
        session_id, valid_trial_count, invalid_trial_count, missed_response_count,
        false_start_count, mean_reaction_time_ms, median_reaction_time_ms,
        min_reaction_time_ms, max_reaction_time_ms, standard_deviation_ms,
        slow_response_count, excluded_artifact_count, classification_code,
        classification_label, classification_explanation, rule_version, app_version
    ) values (
        p_session_id, coalesce(v_valid_count, 0), coalesce(v_excluded_count, 0),
        coalesce(v_missed_count, 0), coalesce(v_false_start_count, 0),
        v_mean, v_median, v_min_ms, v_max_ms, v_stddev, coalesce(v_slow_count, 0),
        v_total_count - coalesce(v_valid_count, 0), v_code, v_label, v_explanation,
        (select rule_version from public.fatigue_rules where id = v_rule_id),
        p_app_version
    )
    on conflict (session_id) do update set
        valid_trial_count = excluded.valid_trial_count,
        mean_reaction_time_ms = excluded.mean_reaction_time_ms,
        classification_code = excluded.classification_code,
        classification_label = excluded.classification_label,
        rule_version = excluded.rule_version,
        app_version = coalesce(excluded.app_version, public.test_results.app_version)
    returning * into v_result;

    -- Per-mode rates (unchanged semantics).
    update public.test_results r set
        accuracy_rate = s.accuracy_rate,
        correct_response_rate = s.correct_rate,
        false_alarm_rate = s.false_alarm_rate,
        omission_rate = s.omission_rate,
        test_mode = v_session.test_mode
    from (
        select
            case when (count(*) filter (where tt.is_target is not null)) = 0 then null
                 else round((count(*) filter (where tt.response_correct is true)
                   + count(*) filter (where tt.is_target is false
                       and coalesce(tt.response_type,'none') = 'none'))::numeric
                   / nullif(count(*) filter (where tt.is_target is not null),0), 6) end as accuracy_rate,
            case when (count(*) filter (where tt.is_target is true)) = 0 then null
                 else round((count(*) filter (where tt.response_correct is true))::numeric
                   / nullif(count(*) filter (where tt.is_target is true),0), 6) end as correct_rate,
            case when (count(*) filter (where tt.is_target is false)) = 0 then null
                 else round((count(*) filter (where tt.is_target is false
                       and tt.response_type in ('correct','wrong','late')))::numeric
                   / nullif(count(*) filter (where tt.is_target is false),0), 6) end as false_alarm_rate,
            case when (count(*) filter (where tt.is_target is true)) = 0 then null
                 else round((count(*) filter (where tt.is_target is true
                       and (tt.response_type = 'none' or tt.missed_response)))::numeric
                   / nullif(count(*) filter (where tt.is_target is true),0), 6) end as omission_rate
        from public.test_trials tt where tt.session_id = p_session_id
    ) s where r.session_id = p_session_id;

    update public.test_sessions
       set session_status = 'finalized', completed_at = now()
     where id = p_session_id;

    return v_result;
end $$;


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
set config = jsonb_set(
        jsonb_set(r.config, '{bands}', (
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
    ),
    updated_at = now()
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

