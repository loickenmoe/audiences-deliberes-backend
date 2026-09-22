-- Q-111 : typer la nature des actions historisées.
--
-- `historique_action.action` est une phrase française libre, parfois concaténée — « Modification de
-- la configuration SEUIL_PROROGATIONS ». Filtrer sur cette colonne suppose un `LIKE '%…%'`, qui
-- paraît commode et **ment par omission** dès qu'un libellé change d'un mot : la ligne cesse d'être
-- trouvée sans que rien ne le signale. C'est le préalable à toute vue d'audit digne de ce nom, et la
-- raison pour laquelle le jalon précède l'écran plutôt que de le suivre.
--
-- Le libellé reste en base à côté du code : l'onglet Historique d'un dossier et le journal système
-- ne changent pas d'aspect. Le code étant désormais posé, traduire un jour ce libellé (QF-24) sera
-- un travail purement frontend, sans repasser sur les 42 points d'écriture.

ALTER TABLE historique_action ADD COLUMN code VARCHAR(50);

-- ─────────────────────────────────────────────────────────────────────────────
-- Reprise des lignes antérieures.
--
-- Trente-six libellés étaient des littéraux fixes : la correspondance est exacte. Les six autres
-- étaient construits et se rattrapent par préfixe. Ce qui ne correspond à rien reste `NON_CLASSEE`
-- plutôt que NULL : « on ne sait pas » doit se filtrer, se compter et se voir, là où un nul se
-- glisserait silencieusement hors de chaque requête.
--
-- Sur une base neuve, cette section est un non-événement : aucune migration ne sème d'historique.
-- ─────────────────────────────────────────────────────────────────────────────
UPDATE historique_action SET code = 'DOSSIER_CREE' WHERE code IS NULL AND action = 'Création du dossier';
UPDATE historique_action SET code = 'DOSSIER_AFFECTATION_MODIFIEE' WHERE code IS NULL AND action = 'Modification de l''affectation';
UPDATE historique_action SET code = 'DEROGATION_SEUIL_DEMANDEE' WHERE code IS NULL AND action = 'Demande de dérogation de seuil';
UPDATE historique_action SET code = 'AUDIENCE_PLANIFIEE' WHERE code IS NULL AND action = 'Planification d''une audience';
UPDATE historique_action SET code = 'AUDIENCE_DOUBLON_CONFIRME' WHERE code IS NULL AND action = 'Doublon d''audience confirmé';
UPDATE historique_action SET code = 'AUDIENCE_COMPTE_RENDU_ENREGISTRE' WHERE code IS NULL AND action = 'Compte rendu d''audience enregistré';
UPDATE historique_action SET code = 'AUDIENCE_ANNULEE' WHERE code IS NULL AND action = 'Annulation d''une audience';
UPDATE historique_action SET code = 'ALARME_CREEE' WHERE code IS NULL AND action = 'Création d''une alarme';
UPDATE historique_action SET code = 'ALARME_REPROGRAMMEE' WHERE code IS NULL AND action = 'Reprogrammation d''une alarme';
UPDATE historique_action SET code = 'ALARME_TRAITEE' WHERE code IS NULL AND action = 'Traitement d''une alarme';
UPDATE historique_action SET code = 'DELIBERE_MISE_EN_DELIBERE' WHERE code IS NULL AND action = 'Mise en délibéré';
UPDATE historique_action SET code = 'DELIBERE_ENREGISTRE' WHERE code IS NULL AND action = 'Enregistrement d''un délibéré';
UPDATE historique_action SET code = 'DELIBERE_VIDE' WHERE code IS NULL AND action = 'Délibéré vidé';
UPDATE historique_action SET code = 'DELIBERE_PROROGE' WHERE code IS NULL AND action = 'Prorogation d''un délibéré';
UPDATE historique_action SET code = 'DELIBERE_RABATTU' WHERE code IS NULL AND action = 'Rabattement du délibéré';
UPDATE historique_action SET code = 'DELIBERE_EXPEDITION_MODIFIEE' WHERE code IS NULL AND action = 'Modification du statut d''expédition';
UPDATE historique_action SET code = 'FRAIS_DEMANDE_DEPOSEE' WHERE code IS NULL AND action = 'Dépôt d''une demande de frais';
UPDATE historique_action SET code = 'FRAIS_CONFORMITE_CONTROLEE' WHERE code IS NULL AND action = 'Contrôle de conformité d''une demande de frais';
UPDATE historique_action SET code = 'FRAIS_OPPORTUNITE_CONTROLEE' WHERE code IS NULL AND action = 'Contrôle d''opportunité d''une demande de frais';
UPDATE historique_action SET code = 'FRAIS_VALIDATION_CONJOINTE' WHERE code IS NULL AND action = 'Validation conjointe d''une demande de frais';
UPDATE historique_action SET code = 'FRAIS_PAIEMENT_ENREGISTRE' WHERE code IS NULL AND action = 'Paiement d''une demande de frais';
UPDATE historique_action SET code = 'DOCUMENT_CHARGE' WHERE code IS NULL AND action = 'Chargement d''un document';
UPDATE historique_action SET code = 'DOCUMENT_SUPPRESSION_DEMANDEE' WHERE code IS NULL AND action = 'Demande de suppression d''un document';
UPDATE historique_action SET code = 'DOCUMENT_SUPPRIME_DIRECTEMENT' WHERE code IS NULL AND action = 'Suppression directe d''un document (DJ/DJA)';
UPDATE historique_action SET code = 'DOCUMENT_SUPPRESSION_ARBITREE' WHERE code IS NULL AND action = 'Décision sur une demande de suppression de document';
UPDATE historique_action SET code = 'CONSTITUTION_DEMANDEE' WHERE code IS NULL AND action = 'Demande de constitution d''un prestataire';
UPDATE historique_action SET code = 'CONSTITUTION_ARBITREE' WHERE code IS NULL AND action = 'Décision sur une demande de constitution';
UPDATE historique_action SET code = 'PUBLICATION_CR_DEPOSE' WHERE code IS NULL AND action = 'Dépôt d''un compte rendu d''audience';
UPDATE historique_action SET code = 'PUBLICATION_AUTRE_DEPOSEE' WHERE code IS NULL AND action = 'Dépôt d''une autre publication';
UPDATE historique_action SET code = 'PUBLICATION_ARBITREE' WHERE code IS NULL AND action = 'Décision sur une publication';
UPDATE historique_action SET code = 'PUBLICATION_COMMENTEE' WHERE code IS NULL AND action = 'Commentaire sur une publication';
UPDATE historique_action SET code = 'PUBLICATION_CORRESPONDANCE_TRANSMISE' WHERE code IS NULL AND action = 'Correspondance transmise à l''avocat';
UPDATE historique_action SET code = 'ADJUDICATION_ENREGISTREE' WHERE code IS NULL AND action = 'Enregistrement d''une adjudication';
UPDATE historique_action SET code = 'CONDAMNATION_ENREGISTREE' WHERE code IS NULL AND action = 'Enregistrement d''une condamnation pécuniaire';
UPDATE historique_action SET code = 'CONDAMNATION_STATUT_MODIFIE' WHERE code IS NULL AND action = 'Mise à jour du statut d''une condamnation';
UPDATE historique_action SET code = 'JURISPRUDENCE_INDEXEE' WHERE code IS NULL AND action = 'Indexation d''une jurisprudence';

-- Les six libellés construits, rattrapés par préfixe.
UPDATE historique_action SET code = 'ETAPE_STATUT_MODIFIE' WHERE code IS NULL AND action LIKE 'Changement de statut de l''étape %';
UPDATE historique_action SET code = 'ETAPE_OUVERTE_AUTOMATIQUEMENT' WHERE code IS NULL AND action LIKE 'Ouverture automatique de l''étape %';
UPDATE historique_action SET code = 'ETAPE_POSITIONNEE_DIRECTEMENT' WHERE code IS NULL AND action LIKE 'Positionnement direct à l''étape %';
UPDATE historique_action SET code = 'DEROGATION_SEUIL_ARBITREE' WHERE code IS NULL AND action LIKE 'Décision sur la dérogation de seuil :%';
UPDATE historique_action SET code = 'SENSIBILITE_ARBITREE' WHERE code IS NULL AND action LIKE 'Décision sur la sensibilité du dossier :%';
UPDATE historique_action SET code = 'CONFIGURATION_MODIFIEE' WHERE code IS NULL AND action LIKE 'Modification de la configuration %';

UPDATE historique_action SET code = 'NON_CLASSEE' WHERE code IS NULL;
ALTER TABLE historique_action ALTER COLUMN code SET NOT NULL;

-- Contrainte fermée, sur le modèle de `chk_alerte_type` : toute action auditée nouvelle
-- passera donc par une migration. C'est le prix d'un référentiel que la base fait respecter.
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
        'DEROGATION_SEUIL_ARBITREE', 'SENSIBILITE_ARBITREE', 'CONFIGURATION_MODIFIEE',
        'NON_CLASSEE'
    ));

-- Le filtre par nature d'action est le premier qu'on pose dans une enquête, toujours borné par
-- une période. L'index reproduit cette requête : colonne filtrée d'abord, colonne de tri ensuite.
CREATE INDEX idx_historique_code_date ON historique_action(code, date_action DESC);
