-- Q-113 : exporter l'audit, et rendre l'export opposable.
--
-- ─────────────────────────────────────────────────────────────────────────────
-- Pourquoi une table, et pas seulement un fichier rendu à l'écran.
-- ─────────────────────────────────────────────────────────────────────────────
-- Un PDF remis à quelqu'un est un fichier perdu, et un fichier qu'on peut rééditer en trois
-- minutes ne prouve rien. L'archive est donc déposée dans MinIO, et cette table consigne son
-- **empreinte SHA-256** avec la période, l'auteur et l'instant. On peut alors démontrer, des années
-- plus tard, que le fichier produit est bien celui qui a été scellé — c'est ce qui transforme une
-- copie en pièce.
--
-- C'est aussi ce qui rendait la purge dispensable (NF-CONF-01, tranché le 2026-09-17) : l'export
-- daté et scellé existe indépendamment de toute suppression, qui n'a pas lieu.
CREATE TABLE export_audit (
    id BIGSERIAL PRIMARY KEY,
    utilisateur_id BIGINT NOT NULL REFERENCES utilisateur(id) ON DELETE RESTRICT,
    date_export TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- La période couverte, telle qu'elle a été demandée. Sans elle, l'archive ne dit pas ce qu'elle
    -- prétend contenir, et son empreinte ne prouve que son intégrité, pas son exhaustivité.
    date_debut DATE NOT NULL,
    date_fin DATE NOT NULL,
    perimetre VARCHAR(20) NOT NULL,
    format VARCHAR(10) NOT NULL,
    lignes BIGINT NOT NULL,
    -- SHA-256 en hexadécimal : 64 caractères. `VARCHAR(64)` et non `CHAR(64)` — PostgreSQL rend le
    -- second comme `bpchar`, que la validation de schéma Hibernate refuse face à un `@Column`
    -- ordinaire. Le gain d'un type à longueur fixe serait nul ici, et le padding une surprise.
    empreinte VARCHAR(64) NOT NULL,
    taille_octets BIGINT NOT NULL,
    cle_stockage VARCHAR(255) NOT NULL,
    CONSTRAINT chk_export_audit_format CHECK (format IN ('PDF', 'EXCEL')),
    CONSTRAINT chk_export_audit_perimetre CHECK (perimetre IN ('TOUT', 'HORS_DOSSIER', 'UN_DOSSIER')),
    -- ON DELETE RESTRICT sur l'auteur : supprimer un compte ne doit pas effacer la trace de ce
    -- qu'il a extrait. C'est le même principe que pour `historique_action`.
    CONSTRAINT chk_export_audit_periode CHECK (date_debut <= date_fin)
);

-- La liste se lit du plus récent au plus ancien, `id` départageant les ex æquo (Q-92).
CREATE INDEX idx_export_audit_tri ON export_audit(date_export DESC, id DESC);

-- ─────────────────────────────────────────────────────────────────────────────
-- Deux actions de plus à historiser.
-- ─────────────────────────────────────────────────────────────────────────────
-- `AUDIT_EXPORTE` : extraire l'audit est un acte plus lourd que le consulter — le contenu quitte
-- l'application. `ARCHIVE_TELECHARGEE` : sans elle, l'archive deviendrait un trou plus large que la
-- table qu'elle double, puisqu'on pourrait y puiser sans laisser de trace.
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
        'AUDIT_CONSULTE', 'AUDIT_EXPORTE', 'ARCHIVE_TELECHARGEE', 'NON_CLASSEE'
    ));
