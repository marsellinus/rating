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
