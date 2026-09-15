-- Q-99 : index adossés aux tris des listes paginées.
--
-- Les jalons M17 (Q-92, Q-93) puis M19 ont donné un ordre stable à six listes paginées. Aucun index
-- ne portait sur les colonnes de tri : PostgreSQL devait donc trier l'intégralité du jeu filtré à
-- **chaque page**, pour n'en rendre que vingt lignes.
--
-- Invisible aux volumes actuels. Mais c'est la forme exacte du défaut Q-94 : la vue consolidée d'un
-- client tenait parfaitement jusqu'à ce qu'un client atteigne 310 dossiers, et elle est alors passée
-- à 19 secondes. Le remède est ici gratuit — des index, aucune ligne de code applicatif, aucun
-- changement de contrat.
--
-- Chaque index reproduit **exactement** l'ORDER BY de la requête qu'il sert, colonne de filtre en
-- tête quand il y en a une : un index dont l'ordre diverge du tri demandé ne sera pas utilisé.

-- 1. GET /dossiers (#2) — ORDER BY created_at DESC, id DESC.
CREATE INDEX idx_dossier_tri_liste ON dossier(created_at DESC, id DESC);

-- 2. GET /clients (#9) — même tri.
CREATE INDEX idx_client_tri_liste ON client(created_at DESC, id DESC);

-- 3. GET /frais/demandes (#26) — même tri, filtré par statut de circuit.
--    Deux index : le circuit est presque toujours filtré (chaque profil ouvre sa file), mais la vue
--    complète existe aussi et un index préfixé par statut ne peut pas la servir.
CREATE INDEX idx_demande_frais_tri_liste ON demande_frais(created_at DESC, id DESC);
CREATE INDEX idx_demande_frais_statut_tri ON demande_frais(statut_circuit, created_at DESC, id DESC);

-- 4. GET /alertes/mes-notifications (#20) — ORDER BY date_declenchement DESC, id DESC, toujours
--    restreint au destinataire, avec ou sans filtre de statut. Le compteur de l'en-tête interroge la
--    forme filtrée sur chaque page de l'application : c'est la requête la plus fréquente du système.
CREATE INDEX idx_alerte_destinataire_tri ON alerte(destinataire_id, date_declenchement DESC, id DESC);
CREATE INDEX idx_alerte_destinataire_statut_tri
    ON alerte(destinataire_id, statut, date_declenchement DESC, id DESC);

-- 5. GET /publications (#59) — ORDER BY date_depot, id ; le sens dépend du statut demandé
--    (ordre d'arrivée pour la file à valider, plus récent d'abord sinon). Un index ascendant sert les
--    deux : PostgreSQL le parcourt à l'envers pour un tri descendant.
CREATE INDEX idx_publication_tri_liste ON publication(date_depot, id);
CREATE INDEX idx_publication_statut_tri ON publication(statut_validation, date_depot, id);

-- 6. GET /avocat/mes-publications (portail, Q-96) — ORDER BY date_depot DESC, id DESC, restreint à
--    l'avocat courant. `idx_publication_avocat_id` existe déjà mais ne porte pas le tri.
CREATE INDEX idx_publication_avocat_tri ON publication(avocat_id, date_depot DESC, id DESC);
