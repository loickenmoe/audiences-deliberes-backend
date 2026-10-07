-- N7 — le manquement répété d'un avocat remonte au Directeur Juridique (observation 8).
--
-- **Pourquoi une migration pour une valeur d'énumération.** `chk_alerte_type` est une contrainte
-- fermée, posée en V1 : la base refuse tout type qu'elle ne connaît pas. Une valeur ajoutée côté
-- Java sans passer ici compile, passe les tests qui ne déclenchent pas cette alerte, et **échoue
-- seulement en écriture, en production**, le jour où un avocat manque sa deuxième audience. Le
-- défaut s'est produit trois fois sur ce projet avec la contrainte jumelle des codes d'historique
-- (V24 à V26, puis V43) ; c'est le prix d'un référentiel que la base fait respecter, et il se paie
-- ici, pas plus tard.
--
-- **Ce que l'alerte signale.** Deux audiences consécutives dont l'avocat n'a pas déposé le compte
-- rendu (métier, 2026-10-07). Elle va au DJ, qui décide seul d'envoyer une demande d'explication :
-- l'application constate, elle ne sanctionne pas.

ALTER TABLE alerte DROP CONSTRAINT chk_alerte_type;

ALTER TABLE alerte
    ADD CONSTRAINT chk_alerte_type CHECK (type IN (
        'RAPPEL_AUDIENCE', 'DELAI_RECOURS', 'PROROGATION', 'ALARME',
        'DOUBLON_AFFECTATION', 'DOUBLON_AUDIENCE', 'DOUBLON_FACTURE',
        'NOUVELLE_DEMANDE_FRAIS', 'VALIDATION_CONJOINTE_REQUISE', 'PAIEMENT_EFFECTUE',
        'NOUVELLE_PUBLICATION', 'DOCUMENT_CHARGE', 'SUPPRESSION_DOCUMENT',
        'CONSTITUTION_A_SIGNER', 'CHARGE_MAX_AVOCAT', 'DELIBERE_ATTENDU', 'DOSSIER_INCOMPLET',
        'MANQUEMENT_COMPTE_RENDU'
    ));

-- ============================================================================
-- La grille de notification doit connaître le nouveau type, sans quoi l'alerte
-- s'afficherait dans l'application et ne partirait sur **aucun** canal.
--
-- V28 a posé le principe : les grilles internes portent **tous** les événements, et « le DJ retire
-- ce qu'il ne veut pas, plutôt que de découvrir qu'un événement n'était jamais envoyé ». Un type
-- nouveau s'y ajoute donc par défaut.
--
-- `NOT LIKE` plutôt qu'un remplacement de la valeur entière : la grille est **modifiable par le
-- DJ**, et réécrire la liste effacerait ses choix. On ajoute à la suite de ce qu'il a réglé, et
-- seulement si le type n'y est pas déjà.
-- ============================================================================

UPDATE configuration_systeme
SET valeur = valeur || ',MANQUEMENT_COMPTE_RENDU'
WHERE cle IN ('GRILLE_NOTIFICATION_OUTLOOK', 'GRILLE_NOTIFICATION_TEAMS')
  AND valeur NOT LIKE '%MANQUEMENT_COMPTE_RENDU%';
