-- Q-76 : une audience planifiée peut être annulée (renvoi du tribunal, date erronée). Le statut
-- ANNULEE existait déjà (chk_audience_statut) sans qu'aucun endpoint ne le pose ; l'annulation
-- porte désormais son motif, obligatoire, et sa date.
ALTER TABLE audience ADD COLUMN motif_annulation TEXT;
ALTER TABLE audience ADD COLUMN date_annulation TIMESTAMP;

ALTER TABLE audience ADD CONSTRAINT chk_audience_annulation
    CHECK (statut <> 'ANNULEE' OR (motif_annulation IS NOT NULL AND date_annulation IS NOT NULL));
