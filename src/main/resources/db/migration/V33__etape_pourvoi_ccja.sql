-- Le pourvoi devant la CCJA, étape à part entière (observation métier du 2026-09-25).
--
-- Pourquoi une valeur distincte, et non une seconde ligne « RECOURS_2 ». La Direction Juridique a
-- confirmé qu'un même dossier peut être porté devant la Cour Suprême **et** devant la CCJA, en même
-- temps : ce sont deux ordres juridictionnels — national et supranational OHADA — saisis par des
-- pourvois séparés, chacun avec ses audiences, son délibéré et sa décision. Deux lignes du même
-- type auraient porté le même libellé à l'écran, donc indiscernables au moment de planifier une
-- audience ; et tout le code suppose l'unicité du type par dossier (`findByDossierIdAndType` rend
-- un Optional, `creerEtape` refuse un type déjà présent). Une quatrième valeur préserve l'un et
-- l'autre.
--
-- Pourquoi ce nom. Les étapes sont triées **alphabétiquement** sur cette colonne
-- (`findByDossierIdOrderByTypeAsc`, @Enumerated(STRING)), et l'ordre procédural n'est respecté
-- aujourd'hui que parce que 'INSTANCE' < 'RECOURS_1' < 'RECOURS_2'. 'RECOURS_2_CCJA' vient après
-- 'RECOURS_2' : l'ordre d'affichage et le calcul de l'étape courante restent justes. Une valeur
-- nommée 'CCJA' ou 'CASSATION' se serait rangée avant 'INSTANCE', en silence.
--
-- Aucune donnée n'est migrée : la contrainte s'élargit, elle ne se déplace pas.

ALTER TABLE etape_procedure DROP CONSTRAINT chk_etape_type;

ALTER TABLE etape_procedure ADD CONSTRAINT chk_etape_type
    CHECK (type IN ('INSTANCE', 'RECOURS_1', 'RECOURS_2', 'RECOURS_2_CCJA'));

COMMENT ON COLUMN etape_procedure.type IS
    'Étape de procédure : INSTANCE, RECOURS_1 (appel), RECOURS_2 (pourvoi Cour Suprême), '
    'RECOURS_2_CCJA (pourvoi CCJA). Les deux pourvois sont concurrents, jamais successifs.';
