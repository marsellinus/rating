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
