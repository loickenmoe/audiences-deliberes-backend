-- Q-115 B2 : le numero n'est attribue qu'a la signature.
--
-- ─────────────────────────────────────────────────────────────────────────────
-- Pourquoi revenir sur une contrainte posee en B1.
-- ─────────────────────────────────────────────────────────────────────────────
-- En B1, la lettre naissait a la validation : elle avait donc toujours son numero, et `NOT NULL`
-- allait de soi. En B2 elle nait avec la **demande**, pour que le juriste la redige — et elle peut
-- alors etre rejetee, ou abandonnee.
--
-- Reserver un numero des le brouillon reviendrait a en **bruler** un a chaque demande sans suite.
-- Or ce compteur ne repart jamais a un : les trous s'accumuleraient definitivement dans une serie de
-- courrier officiel, ou l'absence de la lettre n° 35 se remarque et se justifie mal.
--
-- Le numero reste donc vide tant que la lettre est un brouillon. Il est tire de la sequence au
-- moment de la signature — sauf si le redacteur en a impose un, ce que l'unicite ci-dessous
-- continue de proteger.
ALTER TABLE lettre_constitution ALTER COLUMN numero DROP NOT NULL;

-- L'unicite est conservee, et PostgreSQL n'y compte pas les valeurs nulles : plusieurs brouillons
-- sans numero coexistent sans se gener, deux lettres signees ne peuvent pas porter le meme.
