-- Q-106 : index adossés aux filtres et aux recherches ajoutés au jalon M22.
--
-- V14 a indexé les **tris** des listes paginées. M22 y ajoute des **filtres** et une recherche au fil
-- de la frappe : sans index, chacun rendrait à la base ce que V14 venait de lui épargner. Un filtre
-- lent est d'ailleurs pire qu'une liste lente, parce que l'utilisateur le déclenche à chaque frappe.
--
-- Chaque index reproduit la requête qu'il sert : colonne filtrée d'abord, colonnes de tri ensuite.

-- =====================================================================================
-- 1. Filtres de sensibilité sur GET /dossiers (Q-101)
-- =====================================================================================
-- `sensibiliteStatut=EN_ATTENTE` est la **file des sensibilités à valider** du DJ et de la DJA. Elle
-- se lit plusieurs fois par jour et doit rester instantanée quel que soit le nombre de dossiers.
CREATE INDEX idx_dossier_sensibilite_statut_tri
    ON dossier(sensibilite_statut, created_at DESC, id DESC);

-- `est_sensible` est booléen, donc de très faible cardinalité : un index complet y serait presque
-- inutile. L'index **partiel** ne couvre que les dossiers sensibles — la question qu'on pose
-- réellement — et reste minuscule quel que soit le volume total.
CREATE INDEX idx_dossier_sensibles_tri
    ON dossier(created_at DESC, id DESC) WHERE est_sensible = TRUE;

-- =====================================================================================
-- 2. Filtres des notifications (Q-102)
-- =====================================================================================
-- Le destinataire est toujours présent (il vient du jeton) ; le type le complète.
CREATE INDEX idx_alerte_destinataire_type_tri
    ON alerte(destinataire_id, type, date_declenchement DESC, id DESC);
-- « Les notifications de ce dossier » : aucun index ne portait `dossier_id` sur `alerte`.
CREATE INDEX idx_alerte_dossier_tri ON alerte(dossier_id, date_declenchement DESC, id DESC);

-- =====================================================================================
-- 3. Filtres du circuit des frais (Q-103)
-- =====================================================================================
-- `dossier_id` et `avocat_id` sont déjà indexés seuls (V1) ; ces deux-là y ajoutent le tri, pour que
-- la page n'ait pas à trier après avoir filtré.
CREATE INDEX idx_demande_frais_dossier_tri ON demande_frais(dossier_id, created_at DESC, id DESC);
CREATE INDEX idx_demande_frais_avocat_tri ON demande_frais(avocat_id, created_at DESC, id DESC);
-- Filtre par période : la borne haute est inclusive côté service (fin de journée).
CREATE INDEX idx_demande_frais_periode ON demande_frais(created_at);

-- =====================================================================================
-- 4. Historique d'un dossier (Q-100)
-- =====================================================================================
-- `idx_historique_dossier_id` existe mais ne porte pas le tri : la page devait donc trier après
-- filtrage. L'endpoint dédié lit toujours dans cet ordre.
CREATE INDEX idx_historique_dossier_tri ON historique_action(dossier_id, date_action DESC, id DESC);

-- =====================================================================================
-- 5. Recherches partielles — et pourquoi un B-tree n'y suffit pas
-- =====================================================================================
-- Trois recherches cherchent un fragment **au milieu** d'un texte : la référence d'un dossier
-- (Q-72), celle d'une facture (Q-103) et le nom d'un intervenant (Q-105, la recherche au fil de la
-- frappe). Un index B-tree ordonne les chaînes par leur début : il sert `LIKE 'DOS%'` et ne peut
-- **rien** pour `LIKE '%DOS%'`. `idx_dossier_reference` était donc inutilisable par le filtre qu'il
-- semblait couvrir — depuis M16, sans que rien ne le signale.
--
-- Les trigrammes découpent le texte en triplets de caractères et les indexent : la recherche d'un
-- fragment interne devient alors une recherche indexée.
--
-- ⚠ **Prérequis d'exploitation** : `CREATE EXTENSION` demande des droits élevés. Sur le poste de
-- développement, l'utilisateur applicatif les a. **En production, l'extension devra être créée par
-- la DSI** avant le premier démarrage, sans quoi cette migration échouera — l'échec est franc et
-- visible au démarrage, jamais silencieux.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_dossier_reference_trgm ON dossier USING gin (LOWER(reference) gin_trgm_ops);
CREATE INDEX idx_demande_frais_reference_trgm
    ON demande_frais USING gin (LOWER(reference_facture) gin_trgm_ops);
CREATE INDEX idx_intervenant_nom_trgm ON intervenant USING gin (LOWER(nom) gin_trgm_ops);

-- Le tri du référentiel se fait sur le nom : un B-tree le sert, là où le trigramme sert la recherche.
CREATE INDEX idx_intervenant_nom_tri ON intervenant(nom, id);
