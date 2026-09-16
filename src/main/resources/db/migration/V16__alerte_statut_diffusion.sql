-- Q-109 : mesurer le taux de délivrance des alertes (NF-ALR-02).
--
-- NF-ALR-02 fait du canal applicatif le canal **obligatoire**, avec une délivrance supérieure à
-- 99 %. Ce taux n'était mesuré nulle part : l'échec de diffusion se contentait d'une ligne de
-- journal, que personne ne lit. C'était la seule exigence non fonctionnelle dont la rupture était
-- **silencieuse pour tout le monde**, y compris pour la personne qu'elle pénalise — un juriste qui
-- découvre après coup l'audience qu'on ne lui a pas rappelée.
--
-- **Pourquoi trois états et non deux.** `SimpMessagingTemplate.convertAndSendToUser` ne lève
-- aucune exception lorsque le destinataire est déconnecté : elle route vers une file à laquelle
-- personne n'est abonné et retourne normalement. Un simple booléen « poussée réussie » afficherait
-- donc 100 % en permanence, panne comprise. Il faut séparer l'absence de destinataire — normale,
-- couverte par le repli garanti — de la rupture du canal, qui est le vrai défaut.
--
--   DIFFUSEE  le destinataire avait une session WebSocket vivante, la poussée a abouti
--   DIFFEREE  aucune session ouverte ; l'alerte attend dans GET /alertes/mes-notifications
--   ECHEC     la poussée a échoué : le canal temps réel est rompu
--
-- Taux de délivrance NF-ALR-02 = 1 − ECHEC/total. DIFFEREE compte comme délivrée : le canal
-- applicatif comprend la liste des notifications, et l'enregistrement en base est inconditionnel.

ALTER TABLE alerte
    ADD COLUMN statut_diffusion VARCHAR(20),
    ADD COLUMN date_diffusion TIMESTAMP;

-- Les alertes antérieures à cette migration restent à NULL : leur diffusion n'a pas été observée.
-- Les compter comme réussies gonflerait le taux d'une valeur inventée ; les compter comme échouées
-- l'effondrerait tout autant. Elles sont donc exclues du calcul, qui ne porte que sur ce qui a été
-- réellement mesuré. La contrainte tolère NULL pour cette raison, et pour elle seule.
ALTER TABLE alerte
    ADD CONSTRAINT chk_alerte_statut_diffusion
        CHECK (statut_diffusion IS NULL OR statut_diffusion IN ('DIFFUSEE', 'DIFFEREE', 'ECHEC'));

-- L'agrégat est toujours borné par une fenêtre de dates et groupé par issue. L'index reproduit
-- cette requête : colonne filtrée d'abord, colonne groupée ensuite. Partiel, car les lignes non
-- observées n'entrent jamais dans le calcul et n'ont pas à peser dans l'index.
CREATE INDEX idx_alerte_diffusion_fenetre
    ON alerte(date_declenchement, statut_diffusion)
    WHERE statut_diffusion IS NOT NULL;
