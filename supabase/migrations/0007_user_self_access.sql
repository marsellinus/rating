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
