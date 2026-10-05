-- Plusieurs demandeurs et plusieurs défendeurs (métier, 2026-10-03, observations 1 et 3).
--
-- Ce que le métier a demandé : rendre les parties obligatoires à la création, et permettre d'en
-- déclarer plusieurs de chaque côté. Un procès oppose rarement une personne à une autre — une
-- banque assigne un débiteur et sa caution, se défend contre plusieurs héritiers.
--
-- Aucune colonne nouvelle : `dossier.parties` est du JSONB depuis l'origine, et passer d'une valeur
-- unique à une liste ne change que la forme du contenu. C'est l'intérêt de ce choix de stockage,
-- fait en Q-27.
--
-- La forme passe de :    {"demandeur": "X",  "defendeur": "Y"}
--                 à :    {"demandeurs": ["X"], "defendeurs": ["Y"]}
--
-- Une valeur absente ou vide devient une liste vide, et non une liste contenant une chaîne vide :
-- « aucune partie déclarée » et « une partie sans nom » ne sont pas la même chose, et seule la
-- première décrit l'état réel des dossiers ouverts avant cette règle.
--
-- Sur les 341 dossiers de recette, un seul portait des parties. Les 340 autres ressortent avec deux
-- listes vides : ils restent lisibles et modifiables, mais apparaîtront comme incomplets. La règle
-- nouvelle ne vaut que pour ce qui se crée à partir de maintenant — on ne rend pas invalide, d'un
-- coup de migration, ce que le système a lui-même laissé saisir.

UPDATE dossier
   SET parties = jsonb_build_object(
           'demandeurs',
           CASE WHEN COALESCE(trim(parties ->> 'demandeur'), '') <> ''
                THEN jsonb_build_array(trim(parties ->> 'demandeur'))
                ELSE '[]'::jsonb END,
           'defendeurs',
           CASE WHEN COALESCE(trim(parties ->> 'defendeur'), '') <> ''
                THEN jsonb_build_array(trim(parties ->> 'defendeur'))
                ELSE '[]'::jsonb END)
 WHERE parties IS NOT NULL
   AND (parties ? 'demandeur' OR parties ? 'defendeur');

COMMENT ON COLUMN dossier.parties IS
    'Parties au procès, en JSONB : {"demandeurs": [...], "defendeurs": [...]}. '
    'Plusieurs de chaque depuis le 2026-10-05, et au moins un de chaque exigé à la création. '
    'Le client principal n''y figure pas : il est référencé par client_id (Q-27).';
