-- ============================================================================
-- RATIG — seed.sql
-- ============================================================================
-- !!! DEVELOPMENT-ONLY SEED — DO NOT RUN IN PRODUCTION !!!
--
-- This file inserts fake departments, work areas, shifts, workers and the
-- reference protocol/rule so the app is usable immediately in local/dev
-- environments (`supabase db reset`, `supabase start`, or a dedicated dev
-- project). Production must be populated through the app's own admin UI.
--
-- Promoting the FIRST ADMIN (after that admin signs in with Google once,
-- so a pending profiles row exists):
--
--   UPDATE public.profiles
--      SET role = 'super_admin', account_status = 'active'
--    WHERE email = 'admin@example.com';   -- <-- replace with the real e-mail
--
-- Alternatively call the `approve-account` Edge Function with a service-role
-- key to activate a pending account (it never grants the admin role).
--
-- All inserts are idempotent (fixed UUIDs + ON CONFLICT DO NOTHING).
-- ============================================================================

begin;

-- ---------------------------------------------------------------------------
-- Departments (4)
-- ---------------------------------------------------------------------------
insert into public.departments (id, name, description) values
    ('11111111-1111-4111-8111-111111111111', 'Produksi', 'Lini produksi utama'),
    ('11111111-1111-4111-8111-222222222222', 'Logistik', 'Gudang dan distribusi'),
    ('11111111-1111-4111-8111-333333333333', 'Perawatan', 'Maintenance mesin dan fasilitas'),
    ('11111111-1111-4111-8111-444444444444', 'K3L', 'Keselamatan, Kesehatan Kerja dan Lingkungan')
on conflict (id) do nothing;

-- ---------------------------------------------------------------------------
-- Work areas (6, across departments)
-- ---------------------------------------------------------------------------
insert into public.work_areas (id, department_id, name, description) values
    ('22222222-2222-4222-8222-111111111111', '11111111-1111-4111-8111-111111111111', 'Assembly Line 1', 'Perakitan unit'),
    ('22222222-2222-4222-8222-222222222222', '11111111-1111-4111-8111-111111111111', 'Assembly Line 2', 'Perakitan unit'),
    ('22222222-2222-4222-8222-333333333333', '11111111-1111-4111-8111-222222222222', 'Gudang Bahan', 'Penyimpanan bahan baku'),
    ('22222222-2222-4222-8222-444444444444', '11111111-1111-4111-8111-333333333333', 'Workshop Mekanik', 'Perbaikan mekanikal'),
    ('22222222-2222-4222-8222-555555555555', '11111111-1111-4111-8111-333333333333', 'Utility', 'Listrik, air, HVAC'),
    ('22222222-2222-4222-8222-666666666666', '11111111-1111-4111-8111-444444444444', 'Klinik K3', 'Pemeriksaan dan edukasi')
on conflict (id) do nothing;

-- ---------------------------------------------------------------------------
-- Shifts (3, Asia/Jakarta — no DST, fixed UTC+7)
-- ---------------------------------------------------------------------------
insert into public.shifts (id, name, start_time, end_time, timezone) values
    ('33333333-3333-4333-8333-111111111111', 'Pagi',  '07:00', '15:00', 'Asia/Jakarta'),
    ('33333333-3333-4333-8333-222222222222', 'Siang', '15:00', '23:00', 'Asia/Jakarta'),
    ('33333333-3333-4333-8333-333333333333', 'Malam', '23:00', '07:00', 'Asia/Jakarta')
on conflict (id) do nothing;

-- ---------------------------------------------------------------------------
-- Workers (8) — user_id stays NULL until a worker links their Google account
-- ---------------------------------------------------------------------------
insert into public.workers
    (id, employee_number, full_name, email, department_id, work_area_id, job_title, shift_id)
values
    ('44444444-4444-4444-8444-111111111111', 'NIK-0001', 'Budi Santoso',    null, '11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-111111111111', 'Operator',      '33333333-3333-4333-8333-111111111111'),
    ('44444444-4444-4444-8444-222222222222', 'NIK-0002', 'Siti Rahayu',     null, '11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222', 'Operator',      '33333333-3333-4333-8333-222222222222'),
    ('44444444-4444-4444-8444-333333333333', 'NIK-0003', 'Agus Wijaya',     null, '11111111-1111-4111-8111-222222222222', '22222222-2222-4222-8222-333333333333', 'Staf Gudang',   '33333333-3333-4333-8333-333333333333'),
    ('44444444-4444-4444-8444-444444444444', 'NIK-0004', 'Dewi Lestari',    null, '11111111-1111-4111-8111-333333333333', '22222222-2222-4222-8222-444444444444', 'Teknisi',       '33333333-3333-4333-8333-111111111111'),
    ('44444444-4444-4444-8444-555555555555', 'NIK-0005', 'Rudi Hartono',    null, '11111111-1111-4111-8111-333333333333', '22222222-2222-4222-8222-555555555555', 'Teknisi Utility','33333333-3333-4333-8333-222222222222'),
    ('44444444-4444-4444-8444-666666666666', 'NIK-0006', 'Nur Aini',        null, '11111111-1111-4111-8111-444444444444', '22222222-2222-4222-8222-666666666666', 'Perawat K3',    '33333333-3333-4333-8333-111111111111'),
    ('44444444-4444-4444-8444-777777777777', 'NIK-0007', 'Joko Prasetyo',   null, '11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-111111111111', 'Operator',      '33333333-3333-4333-8333-333333333333'),
    ('44444444-4444-4444-8444-888888888888', 'NIK-0008', 'Maria Fransiska', null, '11111111-1111-4111-8111-222222222222', '22222222-2222-4222-8222-333333333333', 'Admin Gudang',  '33333333-3333-4333-8333-111111111111')
on conflict (id) do nothing;

-- ---------------------------------------------------------------------------
-- Active protocol: RATIG Standar v1
-- ---------------------------------------------------------------------------
insert into public.test_protocols
    (id, name, protocol_version, trial_count, stimulus_delay_min_ms, stimulus_delay_max_ms,
     response_timeout_ms, configuration, status)
values
    ('55555555-5555-4555-8555-111111111111',
     'RATIG Standar', 'v1',
     10,          -- trial_count
     1000, 3000,  -- stimulus delay range (ms)
     2000,        -- response timeout (ms)
     '{"interTrialDelayMs": 800, "practiceTrialCount": 0, "minPlausibleReactionMs": 80}',
     'active')
on conflict (id) do nothing;

-- ---------------------------------------------------------------------------
-- Approved fatigue rule for RATIG Standar v1
-- Four reference bands with placeholder labels (severity 1-4); the
-- organization must validate labels/severities before production use.
-- ---------------------------------------------------------------------------
insert into public.fatigue_rules (id, protocol_id, rule_version, config, approval_status)
values
    ('66666666-6666-4666-8666-111111111111',
     '55555555-5555-4555-8555-111111111111',
     'v1',
     '{
        "bands": [
          {"minMs": 0,   "maxMsExclusive": 240, "code": "BAND_1", "label": "Perlu validasi", "severity": 1},
          {"minMs": 240, "maxMsExclusive": 410, "code": "BAND_2", "label": "Perlu validasi", "severity": 2},
          {"minMs": 410, "maxMsExclusive": 580, "code": "BAND_3", "label": "Perlu validasi", "severity": 3},
          {"minMs": 580, "maxMsExclusive": null, "code": "BAND_4", "label": "Perlu validasi", "severity": 4}
        ],
        "slowResponseThresholdMs": 580,
        "logic": "mean_ms_band",
        "slowCountBands": [],
        "minValidTrials": 4,
        "excludeFalseStarts": true,
        "excludeReactionAboveMs": 2000
      }',
     'approved')
on conflict (id) do nothing;

commit;
