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
