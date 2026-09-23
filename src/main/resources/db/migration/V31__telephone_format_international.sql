-- Notification Engine, étape 11 : une seule écriture pour les numéros de téléphone.
--
-- Jusqu'ici, un numéro était enregistré tel qu'il avait été saisi — « 690000000 », « 654 47 67 96 »,
-- « 237699887766 » —, si bien que la même coordonnée pouvait exister sous plusieurs formes,
-- incomparables entre elles. Le format international devient la seule écriture : +237650228936.
--
-- Trois cas sont convertis, après retrait des séparateurs de saisie :
--   +237XXXXXXXXX  déjà au bon format, inchangé
--   237XXXXXXXXX   l'indicatif est déjà là, il ne manque que le +
--   6XXXXXXXX      mobile camerounais — tous commencent par 6, Camtel compris (620 XX XX XX)
--
-- Ce qui ne relève d'aucun de ces cas est **laissé tel quel** : une donnée douteuse doit rester
-- visible et corrigible à l'écran, pas être réécrite au jugé. La validation à la saisie (étape 11)
-- empêche désormais d'en créer de nouvelles.
UPDATE intervenant
   SET telephone = CASE
        -- Numéro camerounais, avec ou sans indicatif : neuf chiffres commençant par 6.
        WHEN regexp_replace(telephone, '[\s.()\-]', '', 'g') ~ '^(\+?237)?6\d{8}$'
            THEN '+237' || right(regexp_replace(telephone, '[\s.()\-]', '', 'g'), 9)
        -- Numéro étranger déjà au format international.
        WHEN regexp_replace(telephone, '[\s.()\-]', '', 'g') ~ '^\+[1-9]\d{7,14}$'
            THEN regexp_replace(telephone, '[\s.()\-]', '', 'g')
        ELSE telephone
    END
 WHERE telephone IS NOT NULL AND telephone <> '';
