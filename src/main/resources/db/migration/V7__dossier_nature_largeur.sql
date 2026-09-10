-- ============================================================================
-- MODULE DOSSIERS — JALON M16
-- Bug — `dossier.nature VARCHAR(50)` (V1__schema_initial.sql) est plus étroite que le référentiel
-- qui l'alimente : `nature_dossier.libelle` est VARCHAR(255), et le service résout la nature par
-- `findByLibelleIgnoreCase`, c'est-à-dire sur le libellé.
--
-- Trois des dix-sept natures de la taxonomie (Annexe A) dépassent 50 caractères et rendaient donc
-- la création de dossier impossible avec un 400 « nature: size must be between 0 and 50 » :
--   · « Hypothèque judiciaire (inscription provisoire, définitive, réalisation) »   71 car.
--   · « Autres procédures spécifiques (arbitral, contentieux administratif) »       67 car.
--   · « Requête aux fins de saisie de créances ou contestation »                    54 car.
--
-- Détecté au jalon F6 du frontend, en sondant l'endpoint plutôt qu'en lisant le contrat (Q-71).
-- La colonne est alignée sur sa source : ce n'est pas un élargissement de confort.
-- ============================================================================

ALTER TABLE dossier ALTER COLUMN nature TYPE VARCHAR(255);
