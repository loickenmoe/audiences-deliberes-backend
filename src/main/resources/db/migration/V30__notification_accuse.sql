-- Notification Engine, étape 9 : accusés de délivrance (NOTIFICATION_ENGINE_PLAN.md ; Q-120).
--
-- Le moteur publie deux topics de suivi : `notification-sent` après un envoi réussi,
-- `notification-failed` après l'échec définitif. On y lit ce qu'il advient réellement des messages.
--
-- **Ce que ces accusés ne disent pas.** Ils ne portent pas notre `eventId` : ils identifient la
-- notification par `notificationId`, un identifiant interne au moteur, que rien ne nous a jamais
-- transmis. On sait donc *combien* de messages sont partis et sur quel canal, mais pas *lequel* des
-- nôtres. Le rattachement ligne à ligne reste ouvert (voir Q-120) ; la table est prête à le recevoir
-- (`notification_envoi_id`), et la colonne reste vide tant que le moteur ne renvoie pas la référence.
--
-- Livraison au moins une fois : le même accusé peut arriver deux fois. La clé d'unicité
-- (notification_id, type) le rend sans conséquence.
CREATE TABLE notification_accuse (
    id                    BIGSERIAL PRIMARY KEY,
    -- Identifiant de la notification côté moteur (UUID v7).
    notification_id       VARCHAR(100) NOT NULL,
    type                  VARCHAR(10)  NOT NULL,
    service_id            VARCHAR(100),
    canal                 VARCHAR(20),
    -- Fournisseur ayant réellement acheminé le message (ozeki, smtp-drh, callbell, graph...).
    fournisseur           VARCHAR(100),
    message_erreur        VARCHAR(1000),
    -- Un échec définitif ne sera pas retenté par le moteur ; un échec temporaire peut l'être.
    definitif             BOOLEAN,
    date_evenement        TIMESTAMP,
    date_reception        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Rattachement à notre file, quand il devient possible (Q-120). Référence libre, sans clé
    -- étrangère : un accusé reste lisible même si la ligne d'envoi a été purgée.
    notification_envoi_id BIGINT,
    CONSTRAINT uk_notification_accuse UNIQUE (notification_id, type),
    CONSTRAINT chk_notification_accuse_type CHECK (type IN ('SENT', 'FAILED'))
);

-- Supervision (écran 41) : fenêtre de dates, puis regroupement par canal et par issue.
CREATE INDEX idx_notification_accuse_supervision
    ON notification_accuse(date_reception, canal, type);

-- Une notification publiée dont aucun accusé n'arrive n'est ni envoyée ni en échec : elle est sans
-- retour. C'est l'état qu'aurait un message rejeté en silence par le moteur — un jeton invalide, par
-- exemple. Le distinguer évite de compter comme délivré ce qui ne l'a jamais été.
ALTER TABLE notification_envoi DROP CONSTRAINT chk_notification_envoi_statut;

ALTER TABLE notification_envoi
    ADD CONSTRAINT chk_notification_envoi_statut
        CHECK (statut IN ('EN_ATTENTE', 'PUBLIEE', 'ENVOYEE', 'ECHEC', 'NON_JOIGNABLE', 'SANS_RETOUR'));
