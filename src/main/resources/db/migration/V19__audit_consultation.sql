-- Q-112 : l'écran d'audit, et deux ajouts qu'il rend nécessaires.
--
-- ─────────────────────────────────────────────────────────────────────────────
-- 1. La consultation de l'audit s'audite elle-même.
-- ─────────────────────────────────────────────────────────────────────────────
-- Le filtre par personne étant autorisé (NF-CONF-01, tranché le 2026-09-17), cet écran permet de
-- suivre nommément l'activité d'un salarié. Tracer qui l'a consulté, et selon quels critères, n'est
-- pas une contrainte imposée au DJ et à la DJA : c'est ce qui leur permet de **démontrer** qu'ils
-- n'ont regardé que ce qu'ils devaient. La protection vaut dans les deux sens.
--
-- Le code s'ajoute à la contrainte fermée posée en V18 — c'est le prix annoncé d'un référentiel que
-- la base fait respecter : toute action auditée nouvelle demande une migration.
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
        'DEROGATION_SEUIL_ARBITREE', 'SENSIBILITE_ARBITREE', 'CONFIGURATION_MODIFIEE',
        'AUDIT_CONSULTE', 'NON_CLASSEE'
    ));

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. L'auteur de la dernière modification d'un paramètre.
-- ─────────────────────────────────────────────────────────────────────────────
-- Le journal des actions système quitte l'écran des paramètres pour rejoindre l'audit : un outil
-- d'enquête n'avait pas sa place sur un écran d'administration. Mais la question qui naît **en
-- regardant un réglage** — « pourquoi vaut-il 14, et qui l'a mis là ? » — mérite une réponse sur
-- place. Elle se lisait jusqu'ici dans le journal ; elle se lira désormais sur la ligne du
-- paramètre, ce qui est plus juste et n'oblige plus à changer d'écran.
--
-- `date_modification` existe déjà et se met à jour seule ; seul l'auteur manquait.
-- ON DELETE SET NULL : un compte supprimé ne doit pas empêcher de lire le paramètre, et
-- `historique_action` conserve de toute façon la trace complète du changement.
ALTER TABLE configuration_systeme
    ADD COLUMN dernier_auteur_id BIGINT REFERENCES utilisateur(id) ON DELETE SET NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Index de l'enquête par personne.
-- ─────────────────────────────────────────────────────────────────────────────
-- « Qu'a fait X le mois dernier ? » est la requête que l'écran d'audit rend possible et que rien ne
-- servait jusqu'ici : V17 n'indexait l'auteur que pour les actions hors dossier, soit 4 % des
-- lignes. Celui-ci couvre tout l'historique, borné par la période, dans l'ordre de lecture.
CREATE INDEX idx_historique_auteur_date ON historique_action(utilisateur_id, date_action DESC);

-- Une enquête part aussi d'un dossier précis, sans filtre d'auteur. V14 avait indexé le tri de
-- l'onglet Historique ; celui-ci sert la même colonne avec la période en second critère.
CREATE INDEX idx_historique_dossier_date ON historique_action(dossier_id, date_action DESC)
    WHERE dossier_id IS NOT NULL;
