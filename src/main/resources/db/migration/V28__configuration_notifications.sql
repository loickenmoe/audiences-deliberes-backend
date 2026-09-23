-- Notification Engine, étape 3 : réglages des notifications externes par le DJ et le DJA
-- (NOTIFICATION_ENGINE_PLAN.md, D1, D2 et D4 ; DECISIONS_AND_OPEN_QUESTIONS.md Q-118).
--
-- Deux niveaux de réglage :
--   * les canaux actifs : CANAUX_NOTIFICATION_DEFAUT (utilisateurs internes, existante) et
--     CANAUX_NOTIFICATION_PRESTATAIRES (nouvelle) ;
--   * la grille : pour chaque canal, la liste des événements qui y sont envoyés.
-- Couper un canal ne perd pas sa grille : on le rallume tel qu'on l'avait laissé.

-- 1. Une grille complète de types d'alerte dépasse 255 caractères.
ALTER TABLE configuration_systeme ALTER COLUMN valeur TYPE VARCHAR(1000);

-- 2. La clé existante, dormante depuis Q-37, devient le réglage des canaux des utilisateurs
--    internes. Sa valeur (APPLICATIF,OUTLOOK) n'est pas touchée : c'est un choix déjà fait.
UPDATE configuration_systeme
   SET description = 'Canaux de notification des utilisateurs internes : applicatif obligatoire, Outlook et Teams optionnels. RG-ALR-03.'
 WHERE cle = 'CANAUX_NOTIFICATION_DEFAUT';

-- 3. Nouvelles clés. Valeurs initiales :
--    * grilles : tous les événements. Le DJ retire ce qu'il ne veut pas, plutôt que de découvrir
--      qu'un événement n'était jamais envoyé ;
--    * canaux prestataires : courriel personnel et SMS. WhatsApp reste éteint tant que l'on ne sait
--      pas si le moteur exige des modèles de message préapprouvés pour ce canal (point ouvert du plan).
INSERT INTO configuration_systeme (cle, valeur, description, modifiable_par) VALUES
    ('CANAUX_NOTIFICATION_PRESTATAIRES', 'COURRIEL_PERSONNEL,SMS',
        'Canaux de notification des prestataires (avocats et autres prestataires) : courriel personnel, SMS, WhatsApp.',
        'ROLE_DJ'),
    ('GRILLE_NOTIFICATION_OUTLOOK',
        'RAPPEL_AUDIENCE,DELAI_RECOURS,PROROGATION,ALARME,DOUBLON_AFFECTATION,DOUBLON_AUDIENCE,DOUBLON_FACTURE,NOUVELLE_DEMANDE_FRAIS,VALIDATION_CONJOINTE_REQUISE,PAIEMENT_EFFECTUE,NOUVELLE_PUBLICATION,DOCUMENT_CHARGE,SUPPRESSION_DOCUMENT,CONSTITUTION_A_SIGNER,CHARGE_MAX_AVOCAT,DELIBERE_ATTENDU,DOSSIER_INCOMPLET',
        'Types d''alerte envoyés par Outlook aux utilisateurs internes.',
        'ROLE_DJ'),
    ('GRILLE_NOTIFICATION_TEAMS',
        'RAPPEL_AUDIENCE,DELAI_RECOURS,PROROGATION,ALARME,DOUBLON_AFFECTATION,DOUBLON_AUDIENCE,DOUBLON_FACTURE,NOUVELLE_DEMANDE_FRAIS,VALIDATION_CONJOINTE_REQUISE,PAIEMENT_EFFECTUE,NOUVELLE_PUBLICATION,DOCUMENT_CHARGE,SUPPRESSION_DOCUMENT,CONSTITUTION_A_SIGNER,CHARGE_MAX_AVOCAT,DELIBERE_ATTENDU,DOSSIER_INCOMPLET',
        'Types d''alerte envoyés par Teams aux utilisateurs internes.',
        'ROLE_DJ'),
    ('GRILLE_NOTIFICATION_COURRIEL_PERSONNEL',
        'CONSTITUTION_VALIDEE,FRAIS_REJETES,PAIEMENT_EFFECTUE,PUBLICATION_ARBITREE,CORRESPONDANCE_TRANSMISE,RAPPEL_AUDIENCE,AFFECTATION_DOSSIER,DELAI_RECOURS,SUPPRESSION_DOCUMENT_ARBITREE',
        'Événements envoyés aux prestataires par courriel personnel.',
        'ROLE_DJ'),
    ('GRILLE_NOTIFICATION_SMS',
        'CONSTITUTION_VALIDEE,FRAIS_REJETES,PAIEMENT_EFFECTUE,PUBLICATION_ARBITREE,CORRESPONDANCE_TRANSMISE,RAPPEL_AUDIENCE,AFFECTATION_DOSSIER,DELAI_RECOURS,SUPPRESSION_DOCUMENT_ARBITREE',
        'Événements envoyés aux prestataires par SMS.',
        'ROLE_DJ'),
    ('GRILLE_NOTIFICATION_WHATSAPP',
        'CONSTITUTION_VALIDEE,FRAIS_REJETES,PAIEMENT_EFFECTUE,PUBLICATION_ARBITREE,CORRESPONDANCE_TRANSMISE,RAPPEL_AUDIENCE,AFFECTATION_DOSSIER,DELAI_RECOURS,SUPPRESSION_DOCUMENT_ARBITREE',
        'Événements envoyés aux prestataires par WhatsApp.',
        'ROLE_DJ');
