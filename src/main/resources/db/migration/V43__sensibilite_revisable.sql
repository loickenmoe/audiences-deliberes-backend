-- N4 — la diligence particulière devient révisable après l'ouverture du dossier
-- (métier, 2026-10-03, observation 5 ; périmètre du retrait arrêté le 2026-10-06).
--
-- Jusqu'ici, `est_sensible` ne se posait qu'à la création : le juriste proposait, le DJ ou la DJA
-- arbitrait, et c'était tout. Une affaire qui se révélait sensible en cours de route ne pouvait plus
-- le devenir, et un dossier devenu ordinaire ne pouvait plus cesser de l'être.
--
-- Une seule colonne manque pour cela : le **motif du retrait**.
--
-- Pourquoi une colonne, et non l'historique seul. `sensibilite_motif_rejet` existe déjà pour le
-- refus d'une proposition ; réemployer cette colonne pour un retrait ferait dire à la fiche qu'une
-- proposition a été rejetée alors qu'elle avait été validée des mois plus tôt. Ce sont deux faits
-- distincts, et la fiche doit pouvoir montrer le second sans aller fouiller l'historique — c'est
-- déjà le choix fait pour le motif de rejet, et pour le motif d'une dérogation de seuil
-- (`audit_seuil_dossier.motif`). Même règle, même traitement.
--
-- Rien n'est rempli pour les dossiers existants : aucun n'a été retiré, la colonne reste nulle.

ALTER TABLE dossier
    ADD COLUMN sensibilite_motif_retrait VARCHAR(255);

COMMENT ON COLUMN dossier.sensibilite_motif_retrait IS
    'Motif du retrait de la diligence particulière, obligatoire et réservé au DJ et à la DJA. '
    'Distinct de sensibilite_motif_rejet, qui porte le refus d''une proposition.';

-- Les deux natures d'action de N4 doivent entrer dans `chk_historique_code`, qui enumere les codes
-- en dur. C'est le piege que V24, V25 et V26 ont documente avant nous : un code ajoute a
-- l'enumeration Java sans passer ici echoue a l'ecriture, et seulement a l'ecriture - donc en
-- production, sur la premiere proposition faite par un juriste.
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
        'CLIENT_MODIFIE', 'INTERVENANT_MODIFIE', 'NON_CLASSEE'));
