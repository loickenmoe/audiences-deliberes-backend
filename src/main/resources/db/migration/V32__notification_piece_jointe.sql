-- Notification Engine, étape 12 : pièce jointe d'une notification (D5, lettre de constitution).
--
-- **Le fichier ne passe jamais par Kafka** (Guide V1 §3 bis, « claim-check ») : on demande au moteur
-- une URL de dépôt, on y envoie le fichier directement, puis la notification ne porte qu'une
-- référence — la clé de stockage. Vérifié en conditions réelles : demande, réponse READY, dépôt
-- accepté en HTTP 200.
--
-- L'échange étant asynchrone, une notification à pièce jointe attend sa clé avant de partir, d'où le
-- statut ATTENTE_PIECE_JOINTE. Elle repart en EN_ATTENTE dès que la clé est connue.
--
-- **Courriel uniquement** (D5) : le Guide constate que WhatsApp et Teams perdent la pièce jointe en
-- silence, et que le SMS ne la supporte pas.
ALTER TABLE notification_envoi
    -- Document de la GED à joindre : référence libre, comme dossier_id (module transversal).
    ADD COLUMN piece_jointe_document_id BIGINT,
    ADD COLUMN piece_jointe_nom VARCHAR(255),
    ADD COLUMN piece_jointe_mime VARCHAR(100),
    -- Chemin dans notre propre stockage, d'où le fichier est lu pour être déposé chez le moteur.
    ADD COLUMN piece_jointe_chemin VARCHAR(500),
    -- Clé rendue par le moteur, à citer dans la notification.
    ADD COLUMN piece_jointe_cle VARCHAR(500);

ALTER TABLE notification_envoi DROP CONSTRAINT chk_notification_envoi_statut;

ALTER TABLE notification_envoi
    ADD CONSTRAINT chk_notification_envoi_statut
        CHECK (statut IN ('EN_ATTENTE', 'ATTENTE_PIECE_JOINTE', 'PUBLIEE', 'ENVOYEE', 'ECHEC',
                          'NON_JOIGNABLE', 'SANS_RETOUR'));

-- Le moteur répond par le requestId que nous avons choisi — l'event_id de la ligne. L'index sert
-- cette recherche, faite à chaque réponse reçue.
CREATE INDEX idx_notification_envoi_piece_jointe
    ON notification_envoi(statut)
    WHERE statut = 'ATTENTE_PIECE_JOINTE';
