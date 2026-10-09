-- ============================================================================
-- RATIG — 0003_operational_modes.sql
-- ============================================================================
-- Supersedes the finalize_test_session body from 0002_security.sql (which had
-- a JSON-null handling bug in trial validation and lacked test-mode support).
--
-- Adds:
--   1. workers.nik            — NIK business identity (TEXT UNIQUE, digits,
--                               4-32 chars by server CHECK; app enforces the
--                               company policy length, default 16). NIK is a
--                               business key, NEVER a foreign key: sessions
--                               still reference workers.id (UUID).
--   2. test_sessions:         test_mode ('classic'|'rgb_random'|
--                               'random_button'|'focus_inhibition'),
--                               random_seed (reproducible randomization audit),
--                               mode_config (frozen copy of the protocol's
--                               mode configuration at session start - a
--                               running session never changes config),
--                               nik_snapshot (optional audit snapshot).
--   3. test_trials:           stimulus_kind, is_target, response_type
--                               ('correct'|'wrong'|'late'|'early'|'none'),
--                               response_correct.
--   4. test_results:          accuracy_rate, correct_response_rate,
--                               false_alarm_rate, omission_rate (observation
--                               metrics; NULL for classic mode) + test_mode.
--   5. finalize_test_session v2:
--       - JSON-null-safe trial validation (jsonb_typeof based).
--       - Persists per-trial mode fields.
--       - Mode gating: band classification ONLY for 'classic'; other modes
--         get observation metrics + an explicit explanation, never a
--         fatigue classification (modes are never merged into one score).
--       - Explicit rate formulas (see comments below).
--       - Idempotent: refinalizing a finalized session returns the stored
--         result without duplication (UNCHANGED from 0002).
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1. Workers: NIK business identity
-- ---------------------------------------------------------------------------
alter table public.workers
    add column if not exists nik text;
update public.workers set nik = null where nik = '';
-- Backfill synthetic NIK for pre-existing rows (dev/test only).
update public.workers set nik = '9' || lpad((abs(hashtext(employee_number)) % 100000000000000)::text, 15, '0')
where nik is null;
alter table public.workers alter column nik set not null;
create unique index if not exists workers_nik_unique on public.workers (nik);
alter table public.workers drop constraint if exists workers_nik_format;
alter table public.workers add constraint workers_nik_format check (nik ~ '^[0-9]{4,32}$');

-- ---------------------------------------------------------------------------
-- 2. Sessions: test mode + reproducible randomization + frozen config
-- ---------------------------------------------------------------------------
alter table public.test_sessions
    add column if not exists test_mode text not null default 'classic'
        check (test_mode in ('classic','rgb_random','random_button','focus_inhibition')),
    add column if not exists random_seed text,
    add column if not exists mode_config jsonb not null default '{}'::jsonb,
    add column if not exists nik_snapshot text;

-- ---------------------------------------------------------------------------
-- 3. Trials: stimulus/response mode fields
-- ---------------------------------------------------------------------------
alter table public.test_trials
    add column if not exists stimulus_kind text,
    add column if not exists is_target boolean,
    add column if not exists response_type text,
    add column if not exists response_correct boolean;
alter table public.test_trials drop constraint if exists test_trials_response_type_check;
alter table public.test_trials add constraint test_trials_response_type_check
    check (response_type is null or response_type in ('correct','wrong','late','early','none'));

-- ---------------------------------------------------------------------------
-- 4. Results: per-mode observation rates
-- ---------------------------------------------------------------------------
alter table public.test_results
    add column if not exists accuracy_rate numeric,
    add column if not exists correct_response_rate numeric,
    add column if not exists false_alarm_rate numeric,
    add column if not exists omission_rate numeric,
    add column if not exists test_mode text;

-- ---------------------------------------------------------------------------
-- 5. finalize_test_session v2 (replaces the 0002 body)
-- ---------------------------------------------------------------------------
drop function if exists public.finalize_test_session(uuid, jsonb, text, jsonb);

create function public.finalize_test_session(
  p_session_id uuid, p_trials jsonb, p_app_version text default null, p_device_metadata jsonb default null
) returns jsonb
language plpgsql
security definer
set search_path = public
as $fn$


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

        if coalesce(jsonb_typeof(v_trial -> 'reaction_time_ms'), 'absent') not in ('absent','null')
           and (
                jsonb_typeof(v_trial -> 'reaction_time_ms') <> 'number'
                or (v_trial ->> 'reaction_time_ms') !~ '^\d+$'
           ) then
            raise exception 'INVALID_TRIAL at index %: reaction_time_ms must be a non-negative integer or null', v_idx;
        end if;

        if coalesce(jsonb_typeof(v_trial -> 'stimulus_at_monotonic_ns'), 'absent') not in ('absent','null')
           and (
                jsonb_typeof(v_trial -> 'stimulus_at_monotonic_ns') <> 'number'
                or (v_trial ->> 'stimulus_at_monotonic_ns') !~ '^-?\d+$'
           ) then
            raise exception 'INVALID_TRIAL at index %: stimulus_at_monotonic_ns must be an integer or null', v_idx;
        end if;

        if coalesce(jsonb_typeof(v_trial -> 'tap_at_monotonic_ns'), 'absent') not in ('absent','null')
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
            missed_response,
            stimulus_kind,
            is_target,
            response_type,
            response_correct
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
            coalesce((v_trial ->> 'missed_response')::boolean, false),
            v_trial ->> 'stimulus_kind',
            case when jsonb_typeof(v_trial -> 'is_target') = 'boolean'
                 then (v_trial -> 'is_target')::boolean end,
            v_trial ->> 'response_type',
            case when jsonb_typeof(v_trial -> 'response_correct') = 'boolean'
                 then (v_trial -> 'response_correct')::boolean end
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
    -- Mode gating: band classification applies ONLY to the classic reaction mode.
    if coalesce(v_session.test_mode, 'classic') <> 'classic' then
        v_code := null;
        v_label := null;
        v_explanation := 'Metrik observasi mode ' || v_session.test_mode ||
                         '. Klasifikasi kelelahan hanya diterapkan pada mode reaksi klasik.';
    end if;

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


    -- Per-mode observation rates (explicit formulas; null for classic mode).
    -- correct_response_rate = n_correct / n_target
    -- false_alarm_rate      = n_response_to_distractor / n_distractor
    -- omission_rate         = n_target_with_no_response (incl. missed) / n_target
    -- accuracy_rate         = (n_correct + n_distractor_correctly_ignored) / (n_target + n_distractor)
    update public.test_results r set
        accuracy_rate           = s.accuracy_rate,
        correct_response_rate   = s.correct_rate,
        false_alarm_rate        = s.false_alarm_rate,
        omission_rate           = s.omission_rate,
        test_mode               = v_session.test_mode
    from (
        select
            case when (count(*) filter (where tt.is_target is not null)) = 0 then null
                 else round(
                    ( count(*) filter (where tt.response_correct is true)
                      + count(*) filter (where tt.is_target is false
                                        and tt.response_type is null)
                    )::numeric
                    / nullif(count(*) filter (where tt.is_target is not null), 0), 6)
            end as accuracy_rate,
            case when (count(*) filter (where tt.is_target is true)) = 0 then null
                 else round((count(*) filter (where tt.response_correct is true))::numeric
                    / nullif(count(*) filter (where tt.is_target is true), 0), 6)
            end as correct_rate,
            case when (count(*) filter (where tt.is_target is false)) = 0 then null
                 else round((count(*) filter (where tt.is_target is false
                                             and tt.response_type in ('correct','wrong','late')))::numeric
                    / nullif(count(*) filter (where tt.is_target is false), 0), 6)
            end as false_alarm_rate,
            case when (count(*) filter (where tt.is_target is true)) = 0 then null
                 else round((count(*) filter (where tt.is_target is true
                                             and (tt.response_type = 'none' or tt.missed_response)))::numeric
                    / nullif(count(*) filter (where tt.is_target is true), 0), 6)
            end as omission_rate
        from public.test_trials tt
        where tt.session_id = p_session_id
    ) s
    where r.session_id = p_session_id;
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


$fn$;

grant execute on function public.finalize_test_session(uuid,jsonb,text,jsonb) to authenticated, service_role;
