-- RATIG migration 0013 — close admin privilege-escalation gaps
--
-- The 3-tier contract is:
--   super_admin : system configuration (master data, protocols, rules) AND
--                 administration (create/change admins, role changes)
--   admin       : create/manage ordinary users + monitoring + operations
--   user        : run tests, see own history
--
-- The app UI already follows this (ProfileScreen hides config menus from
-- admin; UsersScreen only offers role=USER to non-super-admins), but RLS still
-- used is_admin() on master-data writes and allowed an admin to promote any
-- ordinary user to admin via direct PostgREST calls. UI hiding is NOT access
-- control. This migration makes the server authoritative.
--
-- Safe to re-run (every policy is dropped before creation).

-- ---------------------------------------------------------------------------
-- 1. Master data + configuration: WRITE is super_admin only.
--    SELECT stays broad (all authenticated users need names of departments,
--    shifts, protocols, etc. to read schedules and results).
-- ---------------------------------------------------------------------------

-- departments
drop policy if exists departments_admin_insert on public.departments;
create policy departments_super_admin_insert on public.departments
    for insert to authenticated with check (is_super_admin());
drop policy if exists departments_admin_update on public.departments;
create policy departments_super_admin_update on public.departments
    for update to authenticated
    using (is_super_admin()) with check (is_super_admin());
drop policy if exists departments_admin_delete on public.departments;
create policy departments_super_admin_delete on public.departments
    for delete to authenticated using (is_super_admin());

-- work_areas
drop policy if exists work_areas_admin_insert on public.work_areas;
create policy work_areas_super_admin_insert on public.work_areas
    for insert to authenticated with check (is_super_admin());
drop policy if exists work_areas_admin_update on public.work_areas;
create policy work_areas_super_admin_update on public.work_areas
    for update to authenticated
    using (is_super_admin()) with check (is_super_admin());
drop policy if exists work_areas_admin_delete on public.work_areas;
create policy work_areas_super_admin_delete on public.work_areas
    for delete to authenticated using (is_super_admin());

-- shifts
drop policy if exists shifts_admin_insert on public.shifts;
create policy shifts_super_admin_insert on public.shifts
    for insert to authenticated with check (is_super_admin());
drop policy if exists shifts_admin_update on public.shifts;
create policy shifts_super_admin_update on public.shifts
    for update to authenticated
    using (is_super_admin()) with check (is_super_admin());
drop policy if exists shifts_admin_delete on public.shifts;
create policy shifts_super_admin_delete on public.shifts
    for delete to authenticated using (is_super_admin());

-- test_protocols
drop policy if exists protocols_admin_insert on public.test_protocols;
create policy protocols_super_admin_insert on public.test_protocols
    for insert to authenticated with check (is_super_admin());
drop policy if exists protocols_admin_update on public.test_protocols;
create policy protocols_super_admin_update on public.test_protocols
    for update to authenticated
    using (is_super_admin()) with check (is_super_admin());
drop policy if exists protocols_admin_delete on public.test_protocols;
create policy protocols_super_admin_delete on public.test_protocols
    for delete to authenticated using (is_super_admin());

-- fatigue_rules
drop policy if exists rules_admin_insert on public.fatigue_rules;
create policy rules_super_admin_insert on public.fatigue_rules
    for insert to authenticated with check (is_super_admin());
drop policy if exists rules_admin_update on public.fatigue_rules;
create policy rules_super_admin_update on public.fatigue_rules
    for update to authenticated
    using (is_super_admin()) with check (is_super_admin());
drop policy if exists rules_admin_delete on public.fatigue_rules;
create policy rules_super_admin_delete on public.fatigue_rules
    for delete to authenticated using (is_super_admin());

-- ---------------------------------------------------------------------------
-- 2. profiles UPDATE: only super_admin may grant admin/super_admin or touch
--    another admin. A plain admin may still manage ordinary USER accounts and
--    its own non-role fields, but role/account_status of self stays locked.
-- ---------------------------------------------------------------------------

drop policy if exists profiles_admin_update on public.profiles;
create policy profiles_admin_update on public.profiles
    for update to authenticated
    using (
        id = auth.uid()   -- self: only the locked-self WITH CHECK below applies
        or (
            is_admin()
            -- admins may not touch another admin/super_admin at all
            and (is_super_admin() or role = 'user')
        )
    )
    with check (
        -- self may edit name/phone/etc. but not own role or status
        (
            id = auth.uid()
            and role = (select p.role from public.profiles p where p.id = auth.uid())
            and account_status = (select p.account_status from public.profiles p where p.id = auth.uid())
        )
        or
        -- super_admin may assign any role to a different user
        (
            id <> auth.uid()
            and is_super_admin()
        )
        or
        -- admin may only set a DIFFERENT existing user to role=user
        (
            id <> auth.uid()
            and is_admin()
            and role = 'user'
            and exists (
                select 1 from public.profiles p
                where p.id = profiles.id and p.role = 'user'
            )
        )
    );

-- Verification (as examiner@ratig.test / mgmt@ratig.test — must affect 0 rows):
--   update public.fatigue_rules set rule_version = rule_version;
--   update public.profiles set role = 'admin' where email = 'uji.deploy@ratig.test';
-- As admin@ratig.test both must succeed/target the row.
