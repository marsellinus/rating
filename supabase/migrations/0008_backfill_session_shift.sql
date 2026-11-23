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
