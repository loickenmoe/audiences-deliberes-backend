-- Run automatically by the official PostgreSQL image on first volume start
-- (docker-entrypoint-initdb.d). The "postgres" container serves two separate databases in the
-- same instance: ${POSTGRES_DB} for Keycloak (created by the image via POSTGRES_DB), and
-- audiences_app for the backend, created here to keep the schemas apart.
CREATE DATABASE audiences_app;
