-- Notification Engine, étape 2 : file d'attente des notifications externes (SMS, courriel,
-- WhatsApp, Teams). Voir NOTIFICATION_ENGINE_PLAN.md, décisions D12 à D16.
--
-- **Pourquoi une file en base.** La ligne est écrite dans la transaction de l'action métier, puis un
-- traitement planifié la remet au Notification Engine. Si l'action est annulée, la notification
-- disparaît avec elle ; si l'Engine est en panne, l'action métier n'est ni bloquée ni annulée.
--
-- **Une ligne par destinataire et par canal**, chacune avec son propre suivi : un SMS peut échouer
-- quand le courriel du même événement aboutit.
--
-- **Références libres** (dossier_id, alerte_id, destinataire_ref), sans clé étrangère, sur le même
-- principe que alerte.dossier_id : le module notification est transversal, et l'historique des
-- envois doit survivre à la suppression de ce qu'il cite.
--
--   EN_ATTENTE     à remettre au transport (prochaine_tentative atteinte)
--   PUBLIEE        remise au transport ; l'accusé de l'Engine n'est pas encore arrivé
--   ENVOYEE        accusé positif reçu sur notification-sent
--   ECHEC          tentatives épuisées, ou échec définitif signalé par l'Engine (D13)
--   NON_JOIGNABLE  canal routé mais coordonnée absente : rien n'est envoyé (D14)
CREATE TABLE notification_envoi (
    id                   BIGSERIAL PRIMARY KEY,
    -- Clé de déduplication transmise à l'Engine : une republication ne double jamais l'envoi.
    event_id             VARCHAR(200) NOT NULL,
    type_evenement       VARCHAR(60)  NOT NULL,
    dossier_id           BIGINT,
    alerte_id            BIGINT,
    destinataire_type    VARCHAR(20)  NOT NULL,
    destinataire_ref     BIGINT,
    canal                VARCHAR(20)  NOT NULL,
    -- Adresse ou numéro effectivement visé ; NULL seulement pour NON_JOIGNABLE.
    destinataire         VARCHAR(320),
    -- Destinataire réel quand la redirection DEV (D10) a remplacé l'adresse : trace de ce qui
    -- serait parti en dehors de DEV.
    destinataire_origine VARCHAR(320),
    sujet                VARCHAR(255),
    -- Effacé pour SMS et WhatsApp dès l'accusé positif (D16) : seul le suivi est conservé.
    corps                TEXT,
    statut               VARCHAR(20)  NOT NULL DEFAULT 'EN_ATTENTE',
    tentatives           INTEGER      NOT NULL DEFAULT 0,
    prochaine_tentative  TIMESTAMP,
    derniere_erreur      VARCHAR(1000),
    -- Statut brut renvoyé par l'Engine (SENT, DELIVERED, FAILED_PERM...), pour le diagnostic.
    statut_engine        VARCHAR(20),
    date_creation        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_publication     TIMESTAMP,
    date_accuse          TIMESTAMP,
    CONSTRAINT uk_notification_envoi_event UNIQUE (event_id),
    CONSTRAINT chk_notification_envoi_statut
        CHECK (statut IN ('EN_ATTENTE', 'PUBLIEE', 'ENVOYEE', 'ECHEC', 'NON_JOIGNABLE')),
    CONSTRAINT chk_notification_envoi_canal
        CHECK (canal IN ('EMAIL', 'SMS', 'WHATSAPP', 'TEAMS')),
    CONSTRAINT chk_notification_envoi_destinataire_type
        CHECK (destinataire_type IN ('UTILISATEUR', 'AVOCAT', 'AUTRE_PRESTATAIRE')),
    CONSTRAINT chk_notification_envoi_joignable
        CHECK (statut = 'NON_JOIGNABLE' OR destinataire IS NOT NULL),
    CONSTRAINT chk_notification_envoi_tentatives CHECK (tentatives >= 0)
);

-- Lecture du traitement planifié : les lignes à remettre, par échéance. Partiel, car les lignes
-- terminées sont de loin les plus nombreuses et n'y entrent jamais.
CREATE INDEX idx_notification_envoi_a_remettre
    ON notification_envoi(prochaine_tentative)
    WHERE statut = 'EN_ATTENTE';

-- Supervision (écran 41) : fenêtre de dates, puis regroupement par canal et statut.
CREATE INDEX idx_notification_envoi_supervision
    ON notification_envoi(date_creation, canal, statut);
