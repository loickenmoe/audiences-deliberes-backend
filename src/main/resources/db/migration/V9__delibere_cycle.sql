-- Q-78 : cycle du délibéré aligné sur les sources (RG-DEL-01/06/07, UC-DEL-04/05, US 3.2/3.4).
-- Un délibéré existe dès la mise en délibéré, SANS résultat : c'est pendant cette attente que le
-- tribunal proroge (« DÉLIBÉRÉ PROROGÉ AU [date] ») ou rabat (« DÉLIBÉRÉ RABATTU LE [date, motif] »).
-- Le résultat, qui « vide » le délibéré, vient ensuite. L'expédition (la grosse) n'existe qu'une
-- fois la décision rendue.
ALTER TABLE delibere ALTER COLUMN resultat DROP NOT NULL;
ALTER TABLE delibere ALTER COLUMN statut_expedition DROP DEFAULT;

ALTER TABLE delibere ADD COLUMN date_rabattement DATE;
ALTER TABLE delibere ADD COLUMN motif_rabattement TEXT;

-- Un rabattement porte sa date et son motif, et ne frappe qu'un délibéré encore en attente.
ALTER TABLE delibere ADD CONSTRAINT chk_delibere_rabattement
    CHECK ((date_rabattement IS NULL) = (motif_rabattement IS NULL));
ALTER TABLE delibere ADD CONSTRAINT chk_delibere_rabattu_sans_resultat
    CHECK (date_rabattement IS NULL OR resultat IS NULL);
-- Pas d'expédition avant la décision.
ALTER TABLE delibere ADD CONSTRAINT chk_delibere_expedition_apres_resultat
    CHECK (resultat IS NOT NULL OR statut_expedition IS NULL);
