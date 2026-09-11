-- Q-84 : identifiant de connexion Keycloak (claim preferred_username) de l'utilisateur. C'est lui
-- que le DJ saisit comme compte d'un avocat ; le sub (keycloak_id) reste l'identité technique.
-- Nul pour un utilisateur provisionné avant Q-84, renseigné à sa requête suivante.
ALTER TABLE utilisateur ADD COLUMN identifiant VARCHAR(255);
CREATE UNIQUE INDEX uk_utilisateur_identifiant ON utilisateur (identifiant);
