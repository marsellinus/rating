-- RATIG migration 0012 — backfill historical classification labels
--
-- Dashboard admin groups results by the label stored at finalize time. Old
-- rows were finalized while the seeded rule still used "Perlu validasi", so the
-- chart shows duplicate placeholder labels even though the active rule now has
-- Normal / Fatigue Ringan / Sedang / Berat.
--
-- This updates ONLY the display label from the band code of the same rule that
-- produced the result. It does NOT touch metrics, codes, severities or sessions
-- (finalized sessions remain immutable).

update public.test_results r
set classification_label = band ->> 'label'
from public.fatigue_rules fr,
     jsonb_array_elements(fr.config -> 'bands') band
where fr.id = r.rule_id
  and band ->> 'code' = r.classification_code
  and r.classification_code in ('BAND_1', 'BAND_2', 'BAND_3', 'BAND_4')
  and r.classification_label is distinct from (band ->> 'label');

-- Verification:
--   select classification_code, classification_label, count(*)
--   from public.test_results group by 1, 2 order by 1;
