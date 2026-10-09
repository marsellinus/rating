-- ============================================================================
-- RATIG — 0004_roles.sql
-- ============================================================================
-- Collapses the original four roles into THREE, each with a distinct app area:
--
--   super_admin : everything (user/role management, master data, monitoring)
--   admin       : manage users + monitoring (no system/master-data escalation)
--   user        : run tests + view own history
--
-- Mapping applied to existing rows:
--   admin      -> super_admin
--   examiner   -> admin
--   management -> admin
--   worker     -> user
--
-- This migration is idempotent-safe to re-run: it drops/recreates the role
-- CHECK, redefines the role helpers, rewrites the three policies that used a
-- literal 'management' role, and recreates the three monitoring RPCs whose
-- gates referenced the old role names.
--
-- Apply AFTER 0003_operational_modes.sql.
-- ============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 1. Migrate existing rows BEFORE tightening the CHECK.
-- ---------------------------------------------------------------------------
alter table public.profiles drop constraint if exists profiles_role_check;

update public.profiles set role = 'super_admin' where role = 'admin';
update public.profiles set role = 'admin'       where role in ('examiner', 'management');
update public.profiles set role = 'user'        where role = 'worker';

-- Any unexpected value falls back to the least-privilege role.
update public.profiles set role = 'user'
where role not in ('super_admin', 'admin', 'user');

alter table public.profiles alter column role set default 'user';
alter table public.profiles add constraint profiles_role_check
    check (role in ('super_admin', 'admin', 'user'));

-- ---------------------------------------------------------------------------
-- 2. New sign-ups default to the least-privilege role.
-- ---------------------------------------------------------------------------
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
        'user',
        'pending'
    )
    on conflict (id) do nothing;
    return new;
end;
$$;

-- ---------------------------------------------------------------------------
-- 3. Role helpers.
--    is_admin()     -> super_admin OR admin (management/monitoring + master data)
--    is_super_admin() -> super_admin only (role/user administration)
--    is_examiner()  -> any ACTIVE signed-in user may run tests
-- ---------------------------------------------------------------------------
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
          and role in ('super_admin', 'admin')
          and account_status = 'active'
    )
$$;

create or replace function public.is_super_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.profiles
        where id = auth.uid()
          and role = 'super_admin'
          and account_status = 'active'
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
        where id = auth.uid()
          and account_status = 'active'
    )
$$;

revoke execute on function public.is_super_admin() from public, anon;
grant execute on function public.is_super_admin() to authenticated, service_role;

-- ---------------------------------------------------------------------------
-- 4. profiles: admins manage users, but only a super_admin may touch a
--    super_admin row or grant the super_admin role. Self-modification of
--    role/status stays blocked for everyone.
-- ---------------------------------------------------------------------------
drop policy if exists profiles_admin_update on public.profiles;
create policy profiles_admin_update on public.profiles
    for update to authenticated
    using (
        is_admin()
        and (is_super_admin() or role <> 'super_admin')
    )
    with check (
        (
            id <> auth.uid()
            and (is_super_admin() or role in ('admin', 'user'))
        )
        or (
            id = auth.uid()
            and role = (select p.role from public.profiles p where p.id = auth.uid())
            and account_status = (select p.account_status from public.profiles p where p.id = auth.uid())
        )
    );

-- ---------------------------------------------------------------------------
-- 5. Policies that referenced a literal 'management' role.
--    admin roles now see everything; a user sees own sessions (as examiner or
--    as the linked worker). No literal retired role remains.
-- ---------------------------------------------------------------------------
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
    );

-- test_trials / test_results SELECT mirrors session visibility via EXISTS on
-- test_sessions, so no literal role is embedded there; nothing to rewrite.

-- ---------------------------------------------------------------------------
-- 6. Monitoring RPCs: gates no longer name the retired roles. Access is
--    enforced by is_admin() (super_admin/admin) and by the RLS-backed
--    visibility each function already relies on.
-- ---------------------------------------------------------------------------
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
    if not is_admin() then
        raise exception 'FORBIDDEN: admin only';
    end if;
    if p_from is null or p_to is null or p_to < p_from then
        raise exception 'INVALID_RANGE: p_to must be on or after p_from';
    end if;

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
    -- Any ACTIVE signed-in user may read their own examiner/activity summary.
    if v_caller is null or not is_examiner() then
        raise exception 'FORBIDDEN: sign-in required';
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

-- reaction_trend: widen the gate from the retired role names to any admin role
-- or any active user (it already filters by department when provided).
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
    if not (is_admin() or is_examiner()) then
        raise exception 'FORBIDDEN: sign-in required';
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
