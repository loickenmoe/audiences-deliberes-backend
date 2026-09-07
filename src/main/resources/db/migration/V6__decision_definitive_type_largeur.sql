-- ============================================================================
-- MODULE DÉCISIONS DÉFINITIVES — JALON M12
-- Bug — `decision_definitive.type VARCHAR(20)` (V1__schema_initial.sql) est trop étroite pour sa
-- propre contrainte CHECK, qui autorise la valeur 'CONDAMNATION_PECUNIAIRE' (23 caractères) :
-- tout INSERT d'une condamnation échoue avec « value too long for type character varying(20) ».
-- Détecté en validation manuelle M12 (Q-62, DECISIONS_AND_OPEN_QUESTIONS.md).
-- ============================================================================

ALTER TABLE decision_definitive ALTER COLUMN type TYPE VARCHAR(30);
