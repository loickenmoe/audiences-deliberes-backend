-- ============================================================================
-- MODULE DÉLIBÉRÉS — JALON M7
-- Suivi du délai de recours (FR-DEL-02, RG-DEL-02/04) : échéance propre à chaque délibéré
-- défavorable/mixte, distincte du mécanisme générique d'alarme (Q-04) — cf. Q-19/Q-42,
-- DECISIONS_AND_OPEN_QUESTIONS.md.
-- ============================================================================

ALTER TABLE delibere ADD COLUMN date_echeance_recours TIMESTAMP;

CREATE INDEX idx_delibere_date_echeance_recours ON delibere(date_echeance_recours);
