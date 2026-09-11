-- ============================================================================
-- DONNÉES DE RÉFÉRENCE — Jalon M2
-- Profils, paramètres de configuration (US §11.2, CONF01-07) et taxonomie des natures
-- de dossiers (RG-DOS-02, Annexe A des spécifications fonctionnelles).
-- ============================================================================

-- ============================================================================
-- 1. PROFILS (7 rôles Keycloak, cf. keycloak/audiences-realm.json)
-- ============================================================================

INSERT INTO profil (code, libelle, description) VALUES
    ('JURISTE',      'Juriste',                       'Crée et suit les dossiers, configure les alarmes, charge des documents.'),
    ('DJ',           'Directeur Juridique',            'Administre le système, valide en dernier ressort, valide conjointement les frais sensibles.'),
    ('DJA',          'Directeur Juridique Adjoint',     'Contrôle l''opportunité des frais, valide conjointement les frais sensibles.'),
    ('ASSISTANTE',   'Assistante de Direction',         'Contrôle la conformité des frais, valide les publications d''avocats, met à jour les paiements.'),
    ('SH',           'Supérieur Hiérarchique',          'Valide (seconde signature) les constitutions de prestataires.'),
    ('AVOCAT',       'Avocat partenaire',               'Dépose des demandes de frais, dépose des comptes rendus d''audience.'),
    ('CONSULTATION', 'Consultation',                    'Lecture seule, transverse à tous les profils internes habilités.');

-- ============================================================================
-- 2. PARAMÈTRES DE CONFIGURATION (US §11.2, CONF01 à CONF07)
-- ============================================================================

INSERT INTO configuration_systeme (cle, valeur, description, modifiable_par) VALUES
    ('SEUIL_MONTANT_DEFAUT', '50000000',
        'Montant (FCFA) à partir duquel une demande de frais requiert la validation conjointe DJ + DJA (dossier non sensible). RG-INT-03.',
        'ROLE_DJ'),
    ('SEUIL_PROROGATIONS', '10',
        'Nombre de prorogations au-delà duquel une alerte est déclenchée au DJ et au DJA. RG-DEL-06.',
        'ROLE_DJ'),
    ('CANAUX_NOTIFICATION_DEFAUT', 'APPLICATIF,OUTLOOK',
        'Canaux utilisés par défaut pour l''envoi des alertes (applicatif obligatoire, Outlook/Teams optionnels). RG-ALR-03.',
        'ROLE_DJ'),
    ('DELAI_VALIDATION_CONJOINTE', '5',
        'Délai maximal (jours) pour que le DJ et le DJA valident conjointement une demande sensible avant relance.',
        'ROLE_DJ'),
    ('TAILLE_MAX_GED', '10485760',
        'Poids maximal (octets) d''un document chargé dans la GED — 10 Mo. RG-GED-08. Coordonné avec la configuration MinIO/Spring (DSI).',
        'ROLE_DJ'),
    ('FORMATS_GED', 'PDF,PNG,JPG,TXT,XLSX',
        'Formats de fichiers acceptés dans la GED. RG-GED-08. Coordonné avec la configuration Spring (DSI).',
        'ROLE_DJ'),
    ('CHARGE_MAX_AVOCAT', '20',
        'Nombre maximal de dossiers actifs par avocat avant déclenchement d''une alerte CHARGE_MAX_AVOCAT. RG-ALR-02. Valeur initiale indicative.',
        'ROLE_DJ');

-- ============================================================================
-- 3. TYPES DE CLIENT SENSIBLE (RG-DOS-09 — liste prédéfinie, gérée par le DJ)
-- ============================================================================
-- Valeurs initiales indicatives (aucune liste n'est fournie par la documentation) ;
-- gérables ensuite par le DJ via l'interface d'administration (à construire).

INSERT INTO type_client_sensible (code, libelle) VALUES
    ('VIP',         'Client VIP'),
    ('ENTREPRISE',  'Entreprise / personne morale'),
    ('PARTICULIER', 'Particulier');

-- ============================================================================
-- 4. TAXONOMIE DES NATURES DE DOSSIERS (RG-DOS-02, Annexe A)
-- ============================================================================

-- A.1 — Activité de recouvrement
INSERT INTO nature_dossier (code, libelle, categorie) VALUES
    ('SAISIE_IMMOBILIERE',            'Saisie immobilière', 'RECOUVREMENT'),
    ('HYPOTHEQUE_JUDICIAIRE',         'Hypothèque judiciaire (inscription provisoire, définitive, réalisation)', 'RECOUVREMENT'),
    ('RECOUVREMENT_LEASING',          'Recouvrement judiciaire sur leasing ou lease-back', 'RECOUVREMENT'),
    ('INTERDICTION_CREDIT',           'Mise à l''index, interdiction de crédit', 'RECOUVREMENT'),
    ('ASSIGNATION_PAIEMENT',          'Assignation en paiement', 'RECOUVREMENT'),
    ('SAISIE_MOBILIERE',              'Saisies mobilières corporelles', 'RECOUVREMENT'),
    ('INJONCTION_PAYER',              'Injonction de payer ou de délivrer', 'RECOUVREMENT'),
    ('SAISIE_CREANCES',               'Requête aux fins de saisie de créances ou contestation', 'RECOUVREMENT'),
    ('RECOUVREMENT_PENAL',            'Recouvrement par voie pénale', 'RECOUVREMENT'),
    ('AUTRES_CONTENTIEUX_RECOUVREMENT', 'Autres contentieux (recouvrement)', 'RECOUVREMENT');

-- A.2/A.3 — Activité d'exploitation, litiges et actions en défense
INSERT INTO nature_dossier (code, libelle, categorie) VALUES
    ('CONTENTIEUX_EXECUTION',         'Contentieux de l''exécution', 'EXPLOITATION_LITIGES'),
    ('CIVIL_COMMERCIAL',              'Civil et commercial', 'EXPLOITATION_LITIGES'),
    ('SOCIAL',                        'Social', 'EXPLOITATION_LITIGES'),
    ('PENAL',                         'Pénal', 'EXPLOITATION_LITIGES'),
    ('ASSIGNATIONS_DIVERSES',         'Assignations (en paiement, en déblocage, etc.)', 'EXPLOITATION_LITIGES'),
    ('RECOUVREMENT_INCIDENT_COMPTE',  'Recouvrement d''incidents de compte', 'EXPLOITATION_LITIGES');

-- A.4 — Autres formes d'audience
INSERT INTO nature_dossier (code, libelle, categorie) VALUES
    ('PROCEDURES_SPECIFIQUES', 'Autres procédures spécifiques (arbitral, contentieux administratif)', 'EXPLOITATION_LITIGES');
