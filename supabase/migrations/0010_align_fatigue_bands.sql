-- RATIG migration 0010 — align fatigue bands with the original desktop RATIG.py
--
-- Desktop rule (classify_fatigue):
--   avg < 240            -> Normal
--   240 <= avg <= 400    -> Fatigue Ringan
--   401 <= avg <= 580    -> Fatigue Sedang
--   avg >= 581           -> Fatigue Berat
--
-- The Android seed previously used exclusive upper bounds 240/400/580, which
-- put exactly 400 ms in "Sedang" and exactly 580 ms in "Berat". This migration
-- shifts the two upper bounds by 1 ms so both platforms classify identically.
--
-- Idempotent: only updates rows still carrying the old boundaries.

update public.fatigue_rules r
set config = jsonb_set(
        jsonb_set(r.config, '{bands}', (
            select jsonb_agg(
                case
                    when band ->> 'code' = 'BAND_2' then
                        jsonb_set(band, '{maxMsExclusive}', '401'::jsonb)
                    when band ->> 'code' = 'BAND_3' then
                        jsonb_set(
                            jsonb_set(band, '{minMs}', '401'::jsonb),
                            '{maxMsExclusive}', '581'::jsonb)
                    when band ->> 'code' = 'BAND_4' then
                        jsonb_set(band, '{minMs}', '581'::jsonb)
                    else band
                end
            )
            from jsonb_array_elements(r.config -> 'bands') band
        ))
    ),
    updated_at = now()
where r.config -> 'bands' @> '[{"code":"BAND_3","maxMsExclusive":580}]'::jsonb;

-- Verification:
--   select rule_version, config -> 'bands' from public.fatigue_rules;
