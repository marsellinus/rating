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
