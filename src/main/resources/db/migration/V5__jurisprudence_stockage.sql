-- ============================================================================
-- MODULE DÉCISIONS DÉFINITIVES — JALON M12
-- FR-DEF-03 (US 8.3) exige un fichier PDF pour chaque entrée jurisprudentielle, mais le script
-- SQL initial de `jurisprudence` (M2) n'a aucune colonne de stockage — même omission que
-- `autre_publication` (Q-56) et `document.chemin_stockage` (Q-07). Le fichier est déposé via le
-- `MinioStorageService` du module `ged`, sans création de ligne `document` GED : `dossierId` est
-- **optionnel** pour une jurisprudence (FR-DEF-03), alors que `document.dossier_id` est `NOT NULL`
-- (M2) — une jurisprudence non rattachée à un dossier ne pourrait donc jamais devenir un
-- `document` GED (cf. Q-58, DECISIONS_AND_OPEN_QUESTIONS.md).
-- ============================================================================

ALTER TABLE jurisprudence ADD COLUMN chemin_stockage VARCHAR(255);
