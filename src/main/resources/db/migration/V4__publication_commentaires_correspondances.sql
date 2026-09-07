-- ============================================================================
-- MODULE PUBLICATIONS — JALON M11
-- Complète le schéma existant (`publication`, `compte_rendu_audience`, `autre_publication`,
-- posés en M2) pour trois lacunes constatées lors de l'implémentation :
--
-- 1. `autre_publication` (PIECE/DECISION/CONCLUSION, US 5.3) n'a aucune colonne de stockage du
--    fichier déposé par l'avocat — mise en parallèle avec Q-07 (`document.chemin_stockage`,
--    omission identique du script SQL initial). Le fichier est déposé via le `MinioStorageService`
--    du module `ged` (dépendance déjà déclarée pour l'Épic 5, FUNCTIONAL_MAP.md §5) sans créer de
--    ligne `document` GED — une publication a son propre cycle de vie (VALIDE/REJETE/restriction
--    d'accès), distinct de celui d'un document GED (suppression avec accord DJ/DJA).
--
-- 2. Aucune table ne permet de tracer le commentaire d'un juriste sur une publication (US 5.5,
--    RG-INT-07 : « le juriste ne commente jamais directement l'avocat »).
--
-- 3. Aucune table ne permet de tracer la correspondance unique que le DJ/DJA transmet en retour à
--    l'avocat (Q-20, US 5.5, RG-INT-07). L'unicité par publication est imposée par la contrainte
--    UNIQUE sur `publication_id` (au plus une correspondance par publication).
-- ============================================================================

ALTER TABLE autre_publication ADD COLUMN chemin_stockage VARCHAR(255);

CREATE TABLE commentaire_publication (
    id BIGSERIAL PRIMARY KEY,
    publication_id BIGINT NOT NULL REFERENCES publication(id) ON DELETE CASCADE,
    auteur_id BIGINT NOT NULL REFERENCES utilisateur(id) ON DELETE RESTRICT,
    contenu TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE correspondance_avocat (
    id BIGSERIAL PRIMARY KEY,
    publication_id BIGINT NOT NULL UNIQUE REFERENCES publication(id) ON DELETE CASCADE,
    auteur_id BIGINT NOT NULL REFERENCES utilisateur(id) ON DELETE RESTRICT,
    contenu TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
