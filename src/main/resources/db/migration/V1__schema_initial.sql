-- ============================================================================
-- SCHEMA INITIAL — DIGITALISATION DES AUDIENCES ET DELIBERES
-- Jalon M2 — cf. DEVELOPMENT_ROADMAP.md, DECISIONS_AND_OPEN_QUESTIONS.md
--
-- Source : Dictionnaire des données (REF-06) + script SQL de conception (REF-05 §12.1),
-- complétés des tables et colonnes manquantes arbitrées lors de l'audit (voir chaque
-- commentaire "Décision Qxx"). Conventions : tables et colonnes en snake_case singulier
-- (cf. PROJECT_CONTEXT.md §8 — le script SQL de référence fait foi sur ce point, malgré
-- la règle "pluriel" énoncée par le dictionnaire).
-- ============================================================================

-- ============================================================================
-- 1. RÉFÉRENTIELS (indépendants)
-- ============================================================================

CREATE TABLE profil (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(20) UNIQUE NOT NULL,      -- JURISTE, DJ, DJA, ASSISTANTE, SH, AVOCAT
    libelle VARCHAR(50) NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE utilisateur (
    id BIGSERIAL PRIMARY KEY,
    keycloak_id VARCHAR(255) UNIQUE NOT NULL,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    profil_id BIGINT NOT NULL REFERENCES profil(id),
    actif BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE client (
    id BIGSERIAL PRIMARY KEY,
    reference VARCHAR(50) UNIQUE NOT NULL,
    nom VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    telephone VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE configuration_systeme (
    id BIGSERIAL PRIMARY KEY,
    cle VARCHAR(50) UNIQUE NOT NULL,
    valeur VARCHAR(255) NOT NULL,
    description TEXT,
    modifiable_par VARCHAR(50) NOT NULL,
    date_modification TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Décision Q-06 : type_client_sensible modélisé comme référentiel dédié (liste "prédéfinie,
-- gérée par le DJ", RG-DOS-09), et non comme une clé configuration_systeme (structure liste,
-- pas valeur scalaire). dossier.type_client_sensible reste VARCHAR libre (non FK), par cohérence
-- avec le traitement de dossier.nature vis-à-vis de sa taxonomie (Annexe A) — non FK non plus.
CREATE TABLE type_client_sensible (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    libelle VARCHAR(100) NOT NULL,
    actif BOOLEAN DEFAULT TRUE
);

-- Taxonomie des natures de dossiers (RG-DOS-02, Annexe A des spécifications). Non FK-contrainte
-- depuis dossier.nature (même traitement que type_client_sensible) : référentiel de gestion pour
-- le DJ (CONF02), la validation stricte du champ "nature" relève du service applicatif (M4).
CREATE TABLE nature_dossier (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    libelle VARCHAR(255) NOT NULL,
    categorie VARCHAR(30) NOT NULL,        -- RECOUVREMENT, EXPLOITATION_LITIGES
    actif BOOLEAN DEFAULT TRUE,
    CONSTRAINT chk_nature_categorie CHECK (categorie IN ('RECOUVREMENT', 'EXPLOITATION_LITIGES'))
);

CREATE TABLE intervenant (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(20) NOT NULL,             -- AVOCAT, AUTRE_PRESTATAIRE (discriminant)
    nom VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    telephone VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE avocat (
    id BIGINT PRIMARY KEY REFERENCES intervenant(id) ON DELETE CASCADE,
    compte_keycloak VARCHAR(255) UNIQUE NOT NULL,
    indicateur_charge DOUBLE PRECISION DEFAULT 0,
    indicateur_performance DOUBLE PRECISION DEFAULT 0
);

CREATE TABLE autre_prestataire (
    id BIGINT PRIMARY KEY REFERENCES intervenant(id) ON DELETE CASCADE,
    notification BOOLEAN DEFAULT FALSE
);

-- ============================================================================
-- 2. CŒUR MÉTIER — Dossiers, Étapes, Audiences, Délibérés, Décisions
-- ============================================================================

CREATE TABLE dossier (
    id BIGSERIAL PRIMARY KEY,
    reference VARCHAR(50) UNIQUE NOT NULL,
    nature VARCHAR(50) NOT NULL,
    categorie VARCHAR(30) NOT NULL,        -- RECOUVREMENT, EXPLOITATION_LITIGES
    juridiction_saisie VARCHAR(100) NOT NULL,
    client_id BIGINT NOT NULL REFERENCES client(id),
    -- Décision Q-27 : une seule définition retenue pour "parties" (le dictionnaire en donnait
    -- deux contradictoires) — {demandeur, defendeur} ; le client est référencé via client_id.
    parties JSONB,
    risque_encouru DOUBLE PRECISION DEFAULT 0,
    recours_exerces TEXT,
    motif_renvoi VARCHAR(255),
    code_agence_origine VARCHAR(20),
    code_agence_contentieuse VARCHAR(20),
    dossier_credit_origine VARCHAR(50),        -- si Recouvrement
    pv_transfert VARCHAR(255),                 -- si Recouvrement
    incident_compte_reference VARCHAR(50),     -- si Exploitation/Litiges
    elements_justificatifs TEXT,               -- si Exploitation/Litiges
    champ_alarme TEXT,
    -- Sensibilité (gouvernance)
    est_sensible BOOLEAN DEFAULT FALSE,
    seuil_montant DOUBLE PRECISION,            -- NULL = utilisation du seuil global (Q-26)
    type_client_sensible VARCHAR(50),
    seuil_origine VARCHAR(20) DEFAULT 'GLOBAL', -- GLOBAL ou DEROGATION
    -- Décision Q-10 : la proposition de sensibilité du juriste est soumise à validation
    -- DJ/DJA (RG-DOS-09, UC-DOS-01 alt. 6-a) ; le dossier est créé immédiatement (201),
    -- estSensible n'a d'effet sur le circuit des frais qu'une fois la sensibilité validée.
    sensibilite_statut VARCHAR(20) DEFAULT 'EN_ATTENTE', -- EN_ATTENTE, VALIDEE, REJETEE
    sensibilite_valide_par BIGINT REFERENCES utilisateur(id) ON DELETE SET NULL,
    sensibilite_motif_rejet VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT chk_dossier_categorie CHECK (categorie IN ('RECOUVREMENT', 'EXPLOITATION_LITIGES')),
    CONSTRAINT chk_seuil_origine CHECK (seuil_origine IN ('GLOBAL', 'DEROGATION')),
    CONSTRAINT chk_sensibilite_statut CHECK (sensibilite_statut IN ('EN_ATTENTE', 'VALIDEE', 'REJETEE')),
    CONSTRAINT chk_risque_positif CHECK (risque_encouru >= 0),
    -- Q-26 : seuil_montant renseigné uniquement en cas de dérogation validée (logique de la
    -- conception/du contrat d'API, retenue contre une lecture littérale stricte de RG-DOS-09).
    CONSTRAINT chk_seuil_coherence CHECK (
        (seuil_origine = 'GLOBAL' AND seuil_montant IS NULL) OR
        (seuil_origine = 'DEROGATION' AND seuil_montant IS NOT NULL)
    ),
    -- NB : reprend l'énoncé littéral de REF-05 §12.1 (semantique OU). La cardinalité exacte
    -- (OU vs ET) des champs conditionnels par catégorie reste ouverte — cf. Q-11, scopée à M4,
    -- non tranchée ici. Cette contrainte pourra être resserrée par une migration ultérieure.
    CONSTRAINT chk_categorie_coherence CHECK (
        (categorie = 'RECOUVREMENT' AND
            (dossier_credit_origine IS NOT NULL OR pv_transfert IS NOT NULL)) OR
        (categorie = 'EXPLOITATION_LITIGES' AND
            (incident_compte_reference IS NOT NULL OR elements_justificatifs IS NOT NULL))
    )
);

-- Décision Q-01 : tables d'affectation absentes de la documentation (RG-DOS-03 : "affecté à un
-- ou plusieurs juristes et avocats" ; POST /dossiers reçoit juristesAffectes[]/avocatsAffectes[]).
CREATE TABLE dossier_juriste (
    dossier_id BIGINT NOT NULL REFERENCES dossier(id) ON DELETE CASCADE,
    utilisateur_id BIGINT NOT NULL REFERENCES utilisateur(id) ON DELETE RESTRICT,
    date_affectation TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    affecte_par BIGINT REFERENCES utilisateur(id) ON DELETE SET NULL,
    PRIMARY KEY (dossier_id, utilisateur_id)
);

CREATE TABLE dossier_avocat (
    dossier_id BIGINT NOT NULL REFERENCES dossier(id) ON DELETE CASCADE,
    avocat_id BIGINT NOT NULL REFERENCES avocat(id) ON DELETE RESTRICT,
    date_affectation TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    affecte_par BIGINT REFERENCES utilisateur(id) ON DELETE SET NULL,
    PRIMARY KEY (dossier_id, avocat_id)
);

CREATE TABLE etape_procedure (
    id BIGSERIAL PRIMARY KEY,
    dossier_id BIGINT NOT NULL REFERENCES dossier(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL,             -- INSTANCE, RECOURS_1, RECOURS_2
    statut_cycle_vie VARCHAR(30) NOT NULL, -- OUVERTURE, EN_COURS, EN_DELIBERE, DELIBERE_VIDE, CLOTURE, ARCHIVE
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT chk_etape_type CHECK (type IN ('INSTANCE', 'RECOURS_1', 'RECOURS_2')),
    CONSTRAINT chk_statut_cycle_vie CHECK (
        statut_cycle_vie IN ('OUVERTURE', 'EN_COURS', 'EN_DELIBERE', 'DELIBERE_VIDE', 'CLOTURE', 'ARCHIVE')
    )
);

CREATE TABLE audience (
    id BIGSERIAL PRIMARY KEY,
    dossier_id BIGINT NOT NULL REFERENCES dossier(id) ON DELETE CASCADE,
    etape_id BIGINT REFERENCES etape_procedure(id) ON DELETE SET NULL,
    date_planifiee DATE NOT NULL,
    date_tenue DATE,
    compte_rendu TEXT,
    statut VARCHAR(20) DEFAULT 'PLANIFIEE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_audience_statut CHECK (statut IN ('PLANIFIEE', 'TENUE', 'ANNULEE')),
    CONSTRAINT chk_audience_dates CHECK (date_tenue IS NULL OR date_tenue >= date_planifiee)
);

-- Décision Q-04 : entité "alarme" absente de la documentation (FR-AUD-03 : objet libre +
-- échéance, rattachée au dossier ET à l'étape ; distincte de "alerte" qui n'a ni objet ni
-- etape_id et dont le type est une énumération fermée). L'échéance d'une alarme génère une
-- alerte (type ALARME, cf. table alerte plus bas).
CREATE TABLE alarme (
    id BIGSERIAL PRIMARY KEY,
    dossier_id BIGINT NOT NULL REFERENCES dossier(id) ON DELETE CASCADE,
    etape_id BIGINT REFERENCES etape_procedure(id) ON DELETE SET NULL,
    objet VARCHAR(255) NOT NULL,
    date_echeance TIMESTAMP NOT NULL,
    statut VARCHAR(20) DEFAULT 'ACTIVE',   -- ACTIVE, TRAITEE
    date_traitement TIMESTAMP,
    alarme_precedente_id BIGINT REFERENCES alarme(id) ON DELETE SET NULL, -- chaîne de reprogrammation (FR-AUD-04)
    created_by BIGINT NOT NULL REFERENCES utilisateur(id) ON DELETE RESTRICT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_alarme_statut CHECK (statut IN ('ACTIVE', 'TRAITEE'))
);

CREATE TABLE delibere (
    id BIGSERIAL PRIMARY KEY,
    dossier_id BIGINT NOT NULL REFERENCES dossier(id) ON DELETE CASCADE,
    etape_id BIGINT REFERENCES etape_procedure(id) ON DELETE SET NULL,
    date_deliberee DATE NOT NULL,
    resultat VARCHAR(20) NOT NULL,         -- FAVORABLE, DEFAVORABLE, MIXTE
    statut_expedition VARCHAR(20) DEFAULT 'A_LEVER', -- A_LEVER, LEVEE
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT chk_delibere_resultat CHECK (resultat IN ('FAVORABLE', 'DEFAVORABLE', 'MIXTE')),
    CONSTRAINT chk_delibere_expedition CHECK (statut_expedition IN ('A_LEVER', 'LEVEE'))
);

CREATE TABLE prorogation (
    id BIGSERIAL PRIMARY KEY,
    delibere_id BIGINT NOT NULL REFERENCES delibere(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    motif VARCHAR(255),
    compteur INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE decision_definitive (
    id BIGSERIAL PRIMARY KEY,
    dossier_id BIGINT NOT NULL REFERENCES dossier(id) ON DELETE CASCADE,
    etape_id BIGINT REFERENCES etape_procedure(id) ON DELETE SET NULL,
    type VARCHAR(20) NOT NULL,             -- ADJUDICATION, CONDAMNATION_PECUNIAIRE
    date_decision DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT chk_decision_type CHECK (type IN ('ADJUDICATION', 'CONDAMNATION_PECUNIAIRE'))
);

CREATE TABLE adjudication (
    id BIGINT PRIMARY KEY REFERENCES decision_definitive(id) ON DELETE CASCADE,
    beneficiaire VARCHAR(100) NOT NULL,
    statut_comptabilisation VARCHAR(30),
    reprise_provision BOOLEAN DEFAULT FALSE,
    restitution_soulte BOOLEAN DEFAULT FALSE,
    mutation BOOLEAN DEFAULT FALSE,
    reliquat DOUBLE PRECISION DEFAULT 0,
    CONSTRAINT chk_reliquat_positif CHECK (reliquat >= 0)
);

CREATE TABLE condamnation_pecuniaire (
    id BIGINT PRIMARY KEY REFERENCES decision_definitive(id) ON DELETE CASCADE,
    sens VARCHAR(20) NOT NULL,             -- BANQUE_REDEVABLE, TIERS_REDEVABLE
    nature_paiement VARCHAR(50) NOT NULL,
    statut_paiement VARCHAR(20) DEFAULT 'EN_ATTENTE', -- EN_ATTENTE, PAYE, RECOUVREMENT_FORCE
    CONSTRAINT chk_condamnation_sens CHECK (sens IN ('BANQUE_REDEVABLE', 'TIERS_REDEVABLE')),
    CONSTRAINT chk_condamnation_statut CHECK (statut_paiement IN ('EN_ATTENTE', 'PAYE', 'RECOUVREMENT_FORCE'))
);

CREATE TABLE jurisprudence (
    id BIGSERIAL PRIMARY KEY,
    dossier_id BIGINT REFERENCES dossier(id) ON DELETE SET NULL,
    type_archive VARCHAR(30) NOT NULL,     -- EXTRAIT_MINUTE, EXPEDITION, GROSSE
    mots_cles TEXT[],
    nature_decision VARCHAR(50) NOT NULL,
    juridiction VARCHAR(100) NOT NULL,
    date_decision DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT chk_jurisprudence_type CHECK (type_archive IN ('EXTRAIT_MINUTE', 'EXPEDITION', 'GROSSE'))
);

-- ============================================================================
-- 3. INTERVENANTS — Demandes de frais et constitutions
-- ============================================================================

CREATE TABLE demande_frais (
    id BIGSERIAL PRIMARY KEY,
    dossier_id BIGINT NOT NULL REFERENCES dossier(id) ON DELETE CASCADE,
    avocat_id BIGINT NOT NULL REFERENCES avocat(id) ON DELETE RESTRICT,
    montant DOUBLE PRECISION NOT NULL,
    pieces_justificatives TEXT[],
    statut_circuit VARCHAR(30) NOT NULL,   -- DEPOSEE, CONFORMITE, OPPORTUNITE, VALIDEE, REJETEE, PAYEE
    motif_rejet TEXT,
    statut_paiement VARCHAR(20) DEFAULT 'EN_ATTENTE', -- EN_ATTENTE, PAYEE
    reference_facture VARCHAR(50) UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT chk_montant_positif CHECK (montant >= 0),
    CONSTRAINT chk_demande_statut_circuit CHECK (
        statut_circuit IN ('DEPOSEE', 'CONFORMITE', 'OPPORTUNITE', 'VALIDEE', 'REJETEE', 'PAYEE')
    ),
    CONSTRAINT chk_demande_statut_paiement CHECK (statut_paiement IN ('EN_ATTENTE', 'PAYEE'))
);

-- Décision Q-05 : demande_frais ne porte qu'un statut_circuit et un motif_rejet uniques —
-- insuffisant pour tracer qui a contrôlé la conformité, qui a contrôlé l'opportunité, et les
-- DEUX accords distincts (DJ et DJA) de la validation conjointe (US 4.5 : "le système enregistre
-- MON accord ; si les DEUX accords sont obtenus...").
CREATE TABLE validation_frais (
    id BIGSERIAL PRIMARY KEY,
    demande_frais_id BIGINT NOT NULL REFERENCES demande_frais(id) ON DELETE CASCADE,
    etape VARCHAR(20) NOT NULL,            -- CONFORMITE, OPPORTUNITE, CONJOINTE_DJ, CONJOINTE_DJA
    utilisateur_id BIGINT NOT NULL REFERENCES utilisateur(id) ON DELETE RESTRICT,
    decision VARCHAR(10) NOT NULL,         -- ACCORD, REJET
    motif VARCHAR(255),
    date_validation TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_validation_etape CHECK (etape IN ('CONFORMITE', 'OPPORTUNITE', 'CONJOINTE_DJ', 'CONJOINTE_DJA')),
    CONSTRAINT chk_validation_decision CHECK (decision IN ('ACCORD', 'REJET'))
);

-- Décision Q-03 : table absente de la documentation (FR-INT-06/07, UC-INT-05/06, US 5.6/5.7 :
-- "Tables constitution (à créer)").
CREATE TABLE demande_constitution (
    id BIGSERIAL PRIMARY KEY,
    dossier_id BIGINT NOT NULL REFERENCES dossier(id) ON DELETE CASCADE,
    prestataire_id BIGINT NOT NULL REFERENCES intervenant(id) ON DELETE RESTRICT,
    demandeur_id BIGINT NOT NULL REFERENCES utilisateur(id) ON DELETE RESTRICT,
    motif VARCHAR(255) NOT NULL,
    statut VARCHAR(20) DEFAULT 'EN_ATTENTE', -- EN_ATTENTE, VALIDEE, REJETEE
    valide_par BIGINT REFERENCES utilisateur(id) ON DELETE SET NULL,
    motif_rejet VARCHAR(255),
    lettre_document_id BIGINT,             -- FK ajoutée après CREATE TABLE document (cf. plus bas)
    date_demande TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    date_decision TIMESTAMP,
    CONSTRAINT chk_constitution_statut CHECK (statut IN ('EN_ATTENTE', 'VALIDEE', 'REJETEE'))
);

-- ============================================================================
-- 4. PUBLICATIONS — Dépôts de l'avocat (comptes rendus, pièces)
-- ============================================================================

CREATE TABLE publication (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(20) NOT NULL,             -- CR_AUDIENCE, AUTRE
    avocat_id BIGINT NOT NULL REFERENCES avocat(id) ON DELETE RESTRICT,
    dossier_id BIGINT NOT NULL REFERENCES dossier(id) ON DELETE CASCADE,
    date_depot TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    statut_validation VARCHAR(20) DEFAULT 'DEPOSE', -- DEPOSE, VALIDE, REJETE
    motif_rejet TEXT,
    restriction_acces BIGINT REFERENCES utilisateur(id) ON DELETE SET NULL, -- NULL = tous
    CONSTRAINT chk_publication_type CHECK (type IN ('CR_AUDIENCE', 'AUTRE')),
    CONSTRAINT chk_publication_statut CHECK (statut_validation IN ('DEPOSE', 'VALIDE', 'REJETE'))
);

CREATE TABLE compte_rendu_audience (
    id BIGINT PRIMARY KEY REFERENCES publication(id) ON DELETE CASCADE,
    audience_id BIGINT NOT NULL REFERENCES audience(id) ON DELETE RESTRICT,
    contenu TEXT NOT NULL
);

CREATE TABLE autre_publication (
    id BIGINT PRIMARY KEY REFERENCES publication(id) ON DELETE CASCADE,
    type_document VARCHAR(50) NOT NULL     -- PIECE, DECISION, CONCLUSION
);

-- ============================================================================
-- 5. GED ET ALERTES
-- ============================================================================

CREATE TABLE document (
    id BIGSERIAL PRIMARY KEY,
    dossier_id BIGINT NOT NULL REFERENCES dossier(id) ON DELETE CASCADE,
    nom_fichier VARCHAR(255) NOT NULL,
    type_document VARCHAR(50) NOT NULL,
    format VARCHAR(20) NOT NULL,           -- PDF, PNG, JPG, TXT, XLSX
    poids BIGINT NOT NULL,
    auteur_chargement BIGINT NOT NULL REFERENCES utilisateur(id) ON DELETE RESTRICT,
    date_chargement TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    est_supprime BOOLEAN DEFAULT FALSE,
    -- Décision Q-07 : colonne présente au dictionnaire (REF-06 §2.3.1, "chemin ou référence
    -- MinIO") mais absente du script SQL de REF-05 §12.1 — omission corrigée ici.
    chemin_stockage VARCHAR(255) NOT NULL,
    CONSTRAINT chk_document_format CHECK (format IN ('PDF', 'PNG', 'JPG', 'TXT', 'XLSX')),
    CONSTRAINT chk_document_poids CHECK (poids > 0)
);

ALTER TABLE demande_constitution
    ADD CONSTRAINT fk_constitution_lettre FOREIGN KEY (lettre_document_id)
    REFERENCES document(id) ON DELETE SET NULL;

-- Décision Q-02 : table absente de la documentation ; FR-GED-03 critère 1 la cite pourtant
-- explicitement ("le système ... enregistre la demande dans la table demande_suppression").
-- UC-GED-03 alt. 6-a ajoute une relance automatique à 72 h (date_derniere_relance).
CREATE TABLE demande_suppression (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    demandeur_id BIGINT NOT NULL REFERENCES utilisateur(id) ON DELETE RESTRICT,
    motif VARCHAR(255),
    statut VARCHAR(20) DEFAULT 'EN_ATTENTE', -- EN_ATTENTE, APPROUVEE, REJETEE
    valide_par BIGINT REFERENCES utilisateur(id) ON DELETE SET NULL,
    motif_rejet VARCHAR(255),
    date_demande TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    date_decision TIMESTAMP,
    date_derniere_relance TIMESTAMP,
    CONSTRAINT chk_suppression_statut CHECK (statut IN ('EN_ATTENTE', 'APPROUVEE', 'REJETEE'))
);

CREATE TABLE alerte (
    id BIGSERIAL PRIMARY KEY,
    dossier_id BIGINT REFERENCES dossier(id) ON DELETE CASCADE,
    destinataire_id BIGINT NOT NULL REFERENCES utilisateur(id) ON DELETE RESTRICT,
    -- Décision Q-22 : quatre sources documentaires donnent quatre énumérations différentes de
    -- "type" (4 à 13 valeurs selon le document). Liste réunie retenue (voir DECISIONS_AND_OPEN_
    -- QUESTIONS.md Q-22), complétée de ALARME pour les échéances de la table alarme (Q-04).
    type VARCHAR(40) NOT NULL,
    declencheur TEXT NOT NULL,
    canal VARCHAR(20) NOT NULL,            -- APPLICATIF, OUTLOOK, TEAMS
    statut VARCHAR(20) DEFAULT 'DECLENCHEE', -- DECLENCHEE, TRAITEE
    date_declenchement TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    date_traitement TIMESTAMP,
    CONSTRAINT chk_alerte_type CHECK (type IN (
        'RAPPEL_AUDIENCE', 'DELAI_RECOURS', 'PROROGATION', 'ALARME',
        'DOUBLON_AFFECTATION', 'DOUBLON_AUDIENCE', 'DOUBLON_FACTURE',
        'NOUVELLE_DEMANDE_FRAIS', 'VALIDATION_CONJOINTE_REQUISE', 'PAIEMENT_EFFECTUE',
        'NOUVELLE_PUBLICATION', 'DOCUMENT_CHARGE', 'SUPPRESSION_DOCUMENT',
        'CONSTITUTION_A_SIGNER', 'CHARGE_MAX_AVOCAT', 'DELIBERE_ATTENDU', 'DOSSIER_INCOMPLET'
    )),
    CONSTRAINT chk_alerte_canal CHECK (canal IN ('APPLICATIF', 'OUTLOOK', 'TEAMS')),
    CONSTRAINT chk_alerte_statut CHECK (statut IN ('DECLENCHEE', 'TRAITEE'))
);

-- ============================================================================
-- 6. TRAÇABILITÉ
-- ============================================================================

CREATE TABLE historique_action (
    id BIGSERIAL PRIMARY KEY,
    dossier_id BIGINT REFERENCES dossier(id) ON DELETE CASCADE,
    utilisateur_id BIGINT NOT NULL REFERENCES utilisateur(id) ON DELETE RESTRICT,
    action VARCHAR(255) NOT NULL,
    details JSONB,
    date_action TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE audit_seuil_dossier (
    id BIGSERIAL PRIMARY KEY,
    dossier_id BIGINT NOT NULL REFERENCES dossier(id) ON DELETE CASCADE,
    utilisateur_id BIGINT NOT NULL REFERENCES utilisateur(id) ON DELETE RESTRICT,
    ancien_seuil DOUBLE PRECISION,
    nouveau_seuil DOUBLE PRECISION NOT NULL,
    motif VARCHAR(255) NOT NULL,
    valide_par BIGINT REFERENCES utilisateur(id) ON DELETE SET NULL,
    -- Décision Q-29 : colonne exposée par le schéma AuditSeuil du contrat d'API (statut ∈
    -- {EN_ATTENTE, VALIDEE, REJETEE}) mais absente de la table selon le dictionnaire/REF-05.
    statut VARCHAR(20) DEFAULT 'EN_ATTENTE',
    date_action TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_audit_seuil_statut CHECK (statut IN ('EN_ATTENTE', 'VALIDEE', 'REJETEE'))
);

-- ============================================================================
-- 7. INDEX DE PERFORMANCE (NF-PERF-01 : < 2 s) — REF-06 §4 + index des nouvelles tables
-- ============================================================================

CREATE INDEX idx_client_nom ON client(nom);
CREATE INDEX idx_client_reference ON client(reference);

CREATE INDEX idx_dossier_reference ON dossier(reference);
CREATE INDEX idx_dossier_categorie ON dossier(categorie);
CREATE INDEX idx_dossier_client_id ON dossier(client_id);
CREATE INDEX idx_dossier_seuil_origine ON dossier(seuil_origine);

CREATE INDEX idx_dossier_juriste_utilisateur ON dossier_juriste(utilisateur_id);
CREATE INDEX idx_dossier_avocat_avocat ON dossier_avocat(avocat_id);

CREATE INDEX idx_etape_dossier_id ON etape_procedure(dossier_id);
CREATE INDEX idx_etape_statut ON etape_procedure(statut_cycle_vie);

CREATE INDEX idx_audience_dossier_id ON audience(dossier_id);
CREATE INDEX idx_audience_etape_id ON audience(etape_id);
CREATE INDEX idx_audience_date_planifiee ON audience(date_planifiee);

CREATE INDEX idx_alarme_dossier_id ON alarme(dossier_id);
CREATE INDEX idx_alarme_statut ON alarme(statut);
CREATE INDEX idx_alarme_date_echeance ON alarme(date_echeance);

CREATE INDEX idx_delibere_dossier_id ON delibere(dossier_id);
CREATE INDEX idx_delibere_etape_id ON delibere(etape_id);

CREATE INDEX idx_demande_frais_dossier_id ON demande_frais(dossier_id);
CREATE INDEX idx_demande_frais_avocat_id ON demande_frais(avocat_id);
CREATE INDEX idx_demande_frais_statut ON demande_frais(statut_circuit);
CREATE INDEX idx_validation_frais_demande_id ON validation_frais(demande_frais_id);

CREATE INDEX idx_constitution_dossier_id ON demande_constitution(dossier_id);
CREATE INDEX idx_constitution_statut ON demande_constitution(statut);

CREATE INDEX idx_document_dossier_id ON document(dossier_id);
CREATE INDEX idx_suppression_document_id ON demande_suppression(document_id);
CREATE INDEX idx_suppression_statut ON demande_suppression(statut);

CREATE INDEX idx_alerte_destinataire ON alerte(destinataire_id);
CREATE INDEX idx_alerte_statut ON alerte(statut);

CREATE INDEX idx_publication_dossier_id ON publication(dossier_id);
CREATE INDEX idx_publication_avocat_id ON publication(avocat_id);

CREATE INDEX idx_historique_dossier_id ON historique_action(dossier_id);
CREATE INDEX idx_historique_utilisateur_id ON historique_action(utilisateur_id);

CREATE INDEX idx_audit_seuil_dossier_id ON audit_seuil_dossier(dossier_id);

CREATE INDEX idx_configuration_cle ON configuration_systeme(cle);
