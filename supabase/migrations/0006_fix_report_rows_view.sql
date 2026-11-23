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
