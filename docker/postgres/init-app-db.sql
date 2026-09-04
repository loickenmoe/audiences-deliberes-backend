-- Exécuté automatiquement par l'image officielle PostgreSQL au premier démarrage du volume
-- (docker-entrypoint-initdb.d). Le conteneur "postgres" sert deux bases distinctes dans la
-- même instance : ${POSTGRES_DB} pour Keycloak (créée par l'image via POSTGRES_DB), et
-- audiences_app pour le backend applicatif, créée ici pour éviter tout mélange de schémas.
CREATE DATABASE audiences_app;
