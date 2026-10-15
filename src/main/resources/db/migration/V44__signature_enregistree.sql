-- N8 — la signature enregistrée du signataire (métier, 2026-10-03, observation 10).
--
-- Ce que cette colonne est, et ce qu'elle n'est pas. C'est un **confort de présentation** : la
-- lettre de constitution part avec l'image de la signature de celui qui l'a signée, au lieu d'un
-- nom à signer à la main. Elle n'apporte **aucune valeur probante supplémentaire** — ce qui fait
-- preuve, c'est la trace horodatée de qui a signé, que l'application enregistre déjà dans
-- `historique_action` et dans `demande_constitution.valide_par`. Dit autrement : effacer cette
-- image ne retirerait rien à la preuve, et la falsifier n'en créerait aucune.
--
-- **Pourquoi en base, et non dans la GED.** Une signature n'est pas une pièce de dossier : la
-- déposer dans MinIO la ferait apparaître dans des listes de documents, et son chemin circulerait.
-- Elle est de surcroît sensible — qui la détient peut l'apposer ailleurs. Elle reste donc ici, lue
-- par un seul endpoint, réservé à son titulaire (métier, 2026-10-06 : « chacun téléverse la sienne
-- depuis son compte »).
--
-- **Pourquoi sur `utilisateur` et non sur la lettre.** La même personne signe des dizaines de
-- lettres : copier l'image dans chacune la dupliquerait sans raison, et la rendrait impossible à
-- corriger après coup. La lettre, elle, sait déjà qui l'a signée.

ALTER TABLE utilisateur
    ADD COLUMN signature BYTEA,
    ADD COLUMN signature_type VARCHAR(50),
    ADD COLUMN signature_deposee_le TIMESTAMP;

COMMENT ON COLUMN utilisateur.signature IS
    'Image de la signature manuscrite, déposée par son seul titulaire. Confort de présentation : '
    'la preuve de la signature reste la trace horodatée, non cette image.';
COMMENT ON COLUMN utilisateur.signature_type IS
    'Type MIME de l''image : image/png ou image/jpeg, les seuls que le PDF sait apposer.';

-- Qui a ecrit la derniere version de la lettre de constitution.
--
-- Q-115 B2 reservait la plume au juriste : le SH consultait sans pouvoir recrire, « lui donner la
-- plume viderait la seconde signature de son sens ». Le metier a tranche l'inverse (observation 10,
-- 2026-10-06) : le SH corrige avant de signer. La separation des plumes cede, et c'est la
-- tracabilite qui la remplace - d'ou cette colonne, rendue a l'ecran, pour que le juriste voie que
-- sa lettre a ete reecrite et par qui. L'historique le consigne aussi, mais il faut aller le lire.
--
-- Nulle sur les lettres existantes : aucune n'a ete redigee depuis que l'auteur est enregistre, et
-- inventer un redacteur serait pire que l'absence.
ALTER TABLE lettre_constitution
    ADD COLUMN redige_par BIGINT REFERENCES utilisateur (id);

COMMENT ON COLUMN lettre_constitution.redige_par IS
    'Auteur de la derniere redaction : juriste ou SH (metier, 2026-10-06). Ce que la separation '
    'des roles garantissait, la tracabilite le garantit.';
