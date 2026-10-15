-- QF-64 — l'avocat constitué accède aux pièces du dossier que la banque lui partage, et à une note
-- par audience qui lui est adressée.
--
-- **Le besoin.** L'avocat plaide le dossier : il lui faut l'assignation, les conclusions adverses,
-- le jugement. Jusqu'ici il les recevait hors de l'application, par courriel ou sur papier, sans
-- aucune trace. Les faire passer par ici est un gain de sécurité, à condition de poser la règle.
--
-- **La règle, arbitrée par le porteur du projet le 2026-10-15.**
--   1. L'avocat voit une pièce s'il est affecté au dossier **et constitué** (lettre signée par le
--      SH). L'affectation ne fait pas le mandat : c'est la constitution qui le donne.
--   2. Tout juriste partage une pièce, sans validation du DJ.
--   3. Sur un dossier **sensible**, le partage est réservé au DJ/DJA : le juriste le propose, et le
--      DJ ou la DJA le valide avant que l'avocat ne voie quoi que ce soit.
--   4. Une **note à l'avocat**, distincte du compte rendu du juriste qui reste interne (RG-INT-07).
--
-- **Pourquoi des colonnes sur `document`, et non une table de partages.** Une pièce est partagée
-- avec « les avocats constitués du dossier », pas avec une personne : c'est la règle 1, évaluée à
-- chaque lecture, qui dit qui la voit. Une désaffectation ou un changement d'avocat retire donc
-- l'accès sur-le-champ, sans rien à défaire ici. Une table par avocat aurait demandé de révoquer
-- ligne à ligne - et une révocation oubliée laisse une pièce chez un avocat qui n'en est plus un.

-- 1. L'état du partage d'une pièce.
ALTER TABLE document
    ADD COLUMN partage_avocat VARCHAR(20) NOT NULL DEFAULT 'NON',
    -- Qui a partagé, ou proposé le partage sur un dossier sensible.
    ADD COLUMN partage_par BIGINT REFERENCES utilisateur (id),
    ADD COLUMN partage_le TIMESTAMP,
    -- Qui l'a rendue visible sur un dossier sensible : le DJ ou la DJA, jamais le proposant.
    ADD COLUMN partage_valide_par BIGINT REFERENCES utilisateur (id),
    ADD COLUMN partage_valide_le TIMESTAMP,
    ADD CONSTRAINT chk_document_partage_avocat
        CHECK (partage_avocat IN ('NON', 'PROPOSE', 'PARTAGE')),
    -- Un partage sans auteur ni date ne dirait pas qui a décidé de la communication d'une pièce.
    ADD CONSTRAINT chk_document_partage_trace
        CHECK ((partage_avocat = 'NON') = (partage_par IS NULL AND partage_le IS NULL)),
    -- Une validation ne s'écrit que sur une pièce effectivement partagée.
    ADD CONSTRAINT chk_document_partage_validation
        CHECK ((partage_valide_par IS NULL) = (partage_valide_le IS NULL)
               AND (partage_valide_par IS NULL OR partage_avocat = 'PARTAGE'));

-- La lecture de l'avocat : « quelles pièces de ce dossier me sont partagées ? »
CREATE INDEX idx_document_partagees
    ON document (dossier_id)
    WHERE partage_avocat = 'PARTAGE' AND est_supprime = FALSE;

-- La file du DJ et de la DJA : « quels partages attendent ma validation ? »
CREATE INDEX idx_document_partages_proposes
    ON document (partage_le)
    WHERE partage_avocat = 'PROPOSE' AND est_supprime = FALSE;

COMMENT ON COLUMN document.partage_avocat IS
    'NON, PROPOSE (dossier sensible, en attente du DJ/DJA) ou PARTAGE. Visible des seuls avocats '
    'affectés et constitués, vérifié à chaque lecture (QF-64).';

-- 2. La note adressée à l'avocat, par audience. Le compte rendu du juriste reste interne.
ALTER TABLE audience ADD COLUMN note_avocat TEXT;

COMMENT ON COLUMN audience.note_avocat IS
    'Note du juriste destinée à l''avocat constitué. Distincte du compte rendu, qui reste interne '
    '(RG-INT-07, QF-64).';

-- 3. Les codes d'historique. Le piège documenté par V24, V25, V26, V43 puis V46 : un code absent
--    de cette liste fait échouer l'écriture, en production, le jour où l'action a lieu.
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
        'EXPLICATION_DEMANDEE', 'EXPLICATION_REPONDUE',
        'DOCUMENT_PARTAGE_AVOCAT', 'DOCUMENT_PARTAGE_PROPOSE', 'DOCUMENT_PARTAGE_ARBITRE',
        'DOCUMENT_PARTAGE_RETIRE', 'DOCUMENT_CONSULTE_PAR_AVOCAT', 'AUDIENCE_NOTE_AVOCAT_MODIFIEE',
        'NON_CLASSEE'));
