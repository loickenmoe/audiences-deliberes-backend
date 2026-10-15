-- N7 temps 2 — la demande d'explication, et la réponse de l'avocat (observation 8).
--
-- **Ce que le temps 1 laissait en plan.** Il signale à la direction qu'un avocat a manqué deux
-- audiences de suite. Ensuite, rien : le DJ écrivait hors de l'application, et plus personne ne
-- savait qui s'était expliqué. La demande vit donc ici, avec sa réponse.
--
-- **Pourquoi une table, et non une correspondance.** `correspondance_avocat` pend à une
-- publication, par une clé étrangère obligatoire et unique. Or une demande d'explication porte sur
-- une **absence** — il n'y a précisément rien de publié à quoi l'accrocher. Elle pend donc à
-- l'avocat.
--
-- **Pourquoi la réponse dans la même ligne.** Une demande et sa réponse forment un échange unique :
-- une table de réponses autoriserait plusieurs réponses à une même demande, ce que personne n'a
-- demandé, et obligerait chaque lecture à joindre pour savoir si l'on attend encore. Le statut se
-- déduit alors de `date_reponse`, plutôt que d'être stocké en double et de diverger.

CREATE TABLE demande_explication (
    id BIGSERIAL PRIMARY KEY,
    avocat_id BIGINT NOT NULL REFERENCES avocat (id),
    -- Le dossier de la dernière audience manquée : il situe la demande sans la restreindre.
    -- Facultatif, car la demande porte sur une conduite, pas sur une affaire.
    dossier_id BIGINT REFERENCES dossier (id),
    auteur_id BIGINT NOT NULL REFERENCES utilisateur (id),
    contenu TEXT NOT NULL,
    date_demande TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reponse TEXT,
    date_reponse TIMESTAMP,
    -- Une réponse sans date, ou une date sans réponse, ne veut rien dire : les deux vont ensemble.
    CONSTRAINT chk_demande_explication_reponse
        CHECK ((reponse IS NULL) = (date_reponse IS NULL))
);

-- La question posée à chaque relance : « cet avocat a-t-il une demande sans réponse ? »
CREATE INDEX idx_demande_explication_en_attente
    ON demande_explication (avocat_id)
    WHERE date_reponse IS NULL;

COMMENT ON TABLE demande_explication IS
    'Demande d''explication adressée par la direction à un avocat, et sa réponse (N7, observation 8). '
    'Sans réponse, la relance automatique des comptes rendus est suspendue pour cet avocat.';

-- ============================================================================
-- Les trois référentiels fermés que toute valeur nouvelle doit traverser.
--
-- Ils échouent tous les trois au même endroit — en écriture, en production, le jour où l'événement
-- se produit — et c'est pour cela qu'ils sont posés ensemble ici plutôt qu'au fil de l'eau.
-- ============================================================================

-- 1. Les codes d'historique. Le piège documenté par V24, V25, V26 puis V43.
ALTER TABLE historique_action DROP CONSTRAINT chk_historique_code;

ALTER TABLE historique_action
    ADD CONSTRAINT chk_historique_code CHECK (code IN (
        'DOSSIER_CREE', 'DOSSIER_AFFECTATION_MODIFIEE', 'DEROGATION_SEUIL_DEMANDEE',
        'AUDIENCE_PLANIFIEE', 'AUDIENCE_DOUBLON_CONFIRME', 'AUDIENCE_COMPTE_RENDU_ENREGISTRE',
        'AUDIENCE_ANNULEE', 'ALARME_CREEE', 'ALARME_REPROGRAMMEE',
        'ALARME_TRAITEE', 'DELIBERE_MISE_EN_DELIBERE', 'DELIBERE_ENREGISTRE',
        'DELIBERE_VIDE', 'DELIBERE_PROROGE', 'DELIBERE_RABATTU',
        'DELIBERE_EXPEDITION_MODIFIEE', 'FRAIS_DEMANDE_DEPOSEE', 'FRAIS_CONFORMITE_CONTROLEE',
        'FRAIS_OPPORTUNITE_CONTROLEE', 'FRAIS_VALIDATION_CONJOINTE', 'FRAIS_PAIEMENT_ENREGISTRE',
        'DOCUMENT_CHARGE', 'DOCUMENT_SUPPRESSION_DEMANDEE', 'DOCUMENT_SUPPRIME_DIRECTEMENT',
        'DOCUMENT_SUPPRESSION_ARBITREE', 'CONSTITUTION_DEMANDEE', 'CONSTITUTION_ARBITREE',
        'PUBLICATION_CR_DEPOSE', 'PUBLICATION_AUTRE_DEPOSEE', 'PUBLICATION_ARBITREE',
        'PUBLICATION_COMMENTEE', 'PUBLICATION_CORRESPONDANCE_TRANSMISE', 'ADJUDICATION_ENREGISTREE',
        'CONDAMNATION_ENREGISTREE', 'CONDAMNATION_STATUT_MODIFIE', 'JURISPRUDENCE_INDEXEE',
        'ETAPE_STATUT_MODIFIE', 'ETAPE_OUVERTE_AUTOMATIQUEMENT', 'ETAPE_POSITIONNEE_DIRECTEMENT',
        'DEROGATION_SEUIL_ARBITREE', 'SENSIBILITE_PROPOSEE', 'SENSIBILITE_ARBITREE',
        'SENSIBILITE_RETIREE', 'CONFIGURATION_MODIFIEE', 'AUDIT_CONSULTE',
        'AUDIT_EXPORTE', 'ARCHIVE_TELECHARGEE', 'LETTRE_CONSTITUTION_REDIGEE',
        'CLIENT_MODIFIE', 'INTERVENANT_MODIFIE',
        'EXPLICATION_DEMANDEE', 'EXPLICATION_REPONDUE', 'NON_CLASSEE'));

-- 2. Les types d'alerte. Un seul type sert les deux sens de l'échange — la demande qui part vers
--    l'avocat, la réponse qui revient vers la direction : c'est le même fil, et le texte de
--    l'alerte dit lequel. Deux types auraient doublé la grille pour une distinction que le
--    destinataire fait déjà de lui-même.
ALTER TABLE alerte DROP CONSTRAINT chk_alerte_type;

ALTER TABLE alerte
    ADD CONSTRAINT chk_alerte_type CHECK (type IN (
        'RAPPEL_AUDIENCE', 'DELAI_RECOURS', 'PROROGATION', 'ALARME',
        'DOUBLON_AFFECTATION', 'DOUBLON_AUDIENCE', 'DOUBLON_FACTURE',
        'NOUVELLE_DEMANDE_FRAIS', 'VALIDATION_CONJOINTE_REQUISE', 'PAIEMENT_EFFECTUE',
        'NOUVELLE_PUBLICATION', 'DOCUMENT_CHARGE', 'SUPPRESSION_DOCUMENT',
        'CONSTITUTION_A_SIGNER', 'CHARGE_MAX_AVOCAT', 'DELIBERE_ATTENDU', 'DOSSIER_INCOMPLET',
        'MANQUEMENT_COMPTE_RENDU', 'DEMANDE_EXPLICATION'
    ));

-- 3. Les grilles de notification. Sans cela, l'alerte s'afficherait dans l'application sans partir
--    sur aucun canal — le défaut qu'a révélé V45. On ajoute à la suite de ce que la direction a
--    réglé : ces listes lui appartiennent.
UPDATE configuration_systeme
SET valeur = valeur || ',DEMANDE_EXPLICATION'
WHERE cle IN ('GRILLE_NOTIFICATION_OUTLOOK', 'GRILLE_NOTIFICATION_TEAMS')
  AND valeur NOT LIKE '%DEMANDE_EXPLICATION%';

UPDATE configuration_systeme
SET valeur = valeur || ',DEMANDE_EXPLICATION'
WHERE cle IN ('GRILLE_NOTIFICATION_COURRIEL_PERSONNEL', 'GRILLE_NOTIFICATION_SMS',
              'GRILLE_NOTIFICATION_WHATSAPP')
  AND valeur NOT LIKE '%DEMANDE_EXPLICATION%';
