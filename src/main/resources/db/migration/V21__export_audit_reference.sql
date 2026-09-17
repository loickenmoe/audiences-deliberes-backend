-- Q-114 : donner une référence lisible aux archives d'audit.
--
-- ─────────────────────────────────────────────────────────────────────────────
-- Pourquoi une référence à côté de l'empreinte.
-- ─────────────────────────────────────────────────────────────────────────────
-- L'empreinte **prouve**, la référence **désigne**. Ce sont deux métiers différents, et les
-- confondre est ce qui rendait l'empreinte pénible à l'usage : on ne dicte pas soixante-quatre
-- caractères hexadécimaux au téléphone, et on ne les écrit pas dans un courrier de transmission.
--
-- `AUD-2026-0042` s'écrit, se prononce, se retrouve.
ALTER TABLE export_audit ADD COLUMN reference VARCHAR(20);

-- ─────────────────────────────────────────────────────────────────────────────
-- Pourquoi une séquence dédiée, et non l'identifiant de la ligne.
-- ─────────────────────────────────────────────────────────────────────────────
-- La référence doit figurer **dans le PDF**, donc être connue **avant** de produire le fichier —
-- alors que l'identifiant n'existe qu'après l'insertion, laquelle exige l'empreinte, laquelle exige
-- le fichier. Une séquence indépendante rompt ce cercle.
--
-- C'est aussi pourquoi la référence ne peut pas dériver de l'empreinte : tout ce qui décrit le
-- contenu d'un fichier est, par construction, impossible à écrire dedans sans le changer.
--
-- Pas de compteur remis à zéro chaque année : il exigerait un verrou, pour un gain nul. L'année dit
-- quand, le numéro dit lequel.
CREATE SEQUENCE export_audit_reference_seq START WITH 1 INCREMENT BY 1;

-- Reprise des archives déjà produites : elles reçoivent une référence dérivée de leur identifiant,
-- et la séquence repart au-delà pour qu'aucun numéro ne soit servi deux fois.
UPDATE export_audit
SET reference = 'AUD-' || EXTRACT(YEAR FROM date_export)::int || '-' || LPAD(id::text, 4, '0')
WHERE reference IS NULL;

SELECT setval('export_audit_reference_seq', COALESCE((SELECT max(id) FROM export_audit), 0) + 1, false);

ALTER TABLE export_audit ALTER COLUMN reference SET NOT NULL;

-- Unique : une référence qui désignerait deux archives ne désignerait rien.
ALTER TABLE export_audit ADD CONSTRAINT uk_export_audit_reference UNIQUE (reference);

-- ─────────────────────────────────────────────────────────────────────────────
-- Index de la vérification par empreinte.
-- ─────────────────────────────────────────────────────────────────────────────
-- « Ce fichier que je tiens correspond-il à une de vos archives ? » est une recherche par égalité
-- sur l'empreinte, et c'est le geste que l'écran de vérification répète à chaque dépôt de fichier.
CREATE INDEX idx_export_audit_empreinte ON export_audit(empreinte);
