-- Q-115 B1 : la lettre de constitution devient un document, et cesse d'etre un calcul.
--
-- ─────────────────────────────────────────────────────────────────────────────
-- Pourquoi une table, et non des colonnes sur `demande_constitution`.
-- ─────────────────────────────────────────────────────────────────────────────
-- Jusqu'ici la lettre n'existait nulle part : elle etait **derivee** de cinq donnees au moment de
-- la validation, puis oubliee. A la question « pourquoi cette lettre dit-elle ceci ? », la GED ne
-- repondait rien.
--
-- Elle a desormais son contenu, son numero unique et son instant de gel — trois choses qui lui
-- appartiennent et qui n'ont rien a faire dans la table d'arbitrage d'une demande.
CREATE TABLE lettre_constitution (
    id                      BIGSERIAL PRIMARY KEY,
    demande_constitution_id BIGINT       NOT NULL UNIQUE REFERENCES demande_constitution (id) ON DELETE CASCADE,

    -- ── Le numero ────────────────────────────────────────────────────────────
    -- « N° 4 /First Bank/DAJR/DR/CM/ABN/18 ». Le compteur est la valeur qui suit « N° » ; il
    -- **ne repart pas a 1 chaque annee**, il court. Le reste de la ligne est modifiable lettre par
    -- lettre : votre contrat en compte quatre segments, ce gabarit six, et cela varie par service.
    -- Le stocker en une chaine evite d'exiger une migration a chaque convention nouvelle ; l'ecran
    -- le decoupe sur « / ».
    numero                  INTEGER      NOT NULL UNIQUE,
    suffixe_numero          VARCHAR(160) NOT NULL,

    -- ── L'en-tete de la lettre ───────────────────────────────────────────────
    lieu                    VARCHAR(80)  NOT NULL,
    destinataire_nom        VARCHAR(160),
    destinataire_qualite    VARCHAR(120),
    destinataire_ville      VARCHAR(80),
    concerne                VARCHAR(255),
    objet                   VARCHAR(255) NOT NULL,

    -- ── Le corps et la signature ─────────────────────────────────────────────
    -- Pre-compose depuis le gabarit, les pointillés conserves la ou rien n'est certain : ce document
    -- est fait pour etre complete, et le vider de ses pointillés retirerait une possibilite qui
    -- existe aujourd'hui sur le papier.
    corps                   TEXT         NOT NULL,
    signataire              VARCHAR(160) NOT NULL,

    -- ── Le gel ───────────────────────────────────────────────────────────────
    -- Renseigne au moment ou le PDF est produit. Une lettre generee ne se reecrit pas : une
    -- correction donne une **nouvelle** lettre, avec un nouveau numero, et la premiere reste dans la
    -- GED. Une piece qu'on peut retoucher en silence ne prouve rien (meme raison qu'en Q-113).
    date_generation         TIMESTAMP,

    date_creation           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_modification       TIMESTAMP
);

-- ─────────────────────────────────────────────────────────────────────────────
-- Le compteur.
-- ─────────────────────────────────────────────────────────────────────────────
-- Sequence dediee, pour la meme raison qu'en Q-114 : le numero figure **dans** le fichier, il doit
-- donc etre connu **avant** de le produire — alors que l'identifiant de la ligne n'existe qu'apres
-- l'insertion. Une sequence independante rompt ce cercle.
--
-- Elle sert de **proposition** : le numero reste modifiable, et c'est la contrainte d'unicite
-- ci-dessus qui interdit d'en servir deux fois le meme.
CREATE SEQUENCE lettre_constitution_numero_seq START WITH 1 INCREMENT BY 1;

-- Aucun index supplementaire : les deux questions posees a cette table — « la lettre de cette
-- demande » et « a quoi correspond la lettre n° 34 ? » — sont deja servies par les contraintes
-- UNIQUE ci-dessus, qui creent chacune leur index.
