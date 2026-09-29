-- Références attribuées par le système (métier, 2026-09-29).
--
-- ─────────────────────────────────────────────────────────────────────────────
-- Ce que le métier a demandé.
-- ─────────────────────────────────────────────────────────────────────────────
-- « Les champs Référence doivent être automatiquement remplis par le système. » La saisie manuelle
-- posait trois problèmes que la génération supprime d'un coup :
--   * une coquille dans une référence est **définitive** (Q-116 : « le revers est assumé ») ;
--   * le doublon est un mur — le juriste devait deviner une référence libre (409 / ERR-002) ;
--   * une référence saisie **peut contenir un nom de client**, et elle part dans les SMS et les
--     WhatsApp. Le plan du Notification Engine laissait la question ouverte ; une référence
--     attribuée par le système y répond au lieu de la reporter.
--
-- ─────────────────────────────────────────────────────────────────────────────
-- Pourquoi une table de compteurs, et non une séquence comme en V21.
-- ─────────────────────────────────────────────────────────────────────────────
-- V21 numérote les archives d'audit avec une séquence globale : `AUD-2026-0042` puis, en janvier
-- suivant, `AUD-2027-0043`. Le numéro ne repart pas à 1. Pour une archive, personne ne le remarque.
--
-- Pour un dossier, si : `DOS-2027-0158` se lit « le 158e dossier de 2027 », alors qu'il serait le
-- 158e depuis toujours. La référence dirait quelque chose de faux, tous les jours, à tout le monde.
--
-- V21 écartait le compteur annuel parce qu'il « exigerait un verrou, pour un gain nul ». Ici le gain
-- n'est pas nul — et le verrou n'est pas nécessaire : `INSERT … ON CONFLICT DO UPDATE … RETURNING`
-- est atomique et ne verrouille que la ligne de l'année en cours. Deux juristes qui enregistrent à
-- la même seconde obtiennent deux numéros différents, sans que ni l'un ni l'autre n'attende.
--
-- ─────────────────────────────────────────────────────────────────────────────
-- Ce que cette migration ne fait pas.
-- ─────────────────────────────────────────────────────────────────────────────
-- Elle ne touche à aucune référence existante et ne branche rien : le compteur est posé, personne
-- ne l'appelle encore. Renommer des références déjà connues de leurs utilisateurs serait le contraire
-- du but poursuivi — une référence désigne, et ce qui désigne ne se change pas.

CREATE TABLE compteur_reference (
    -- La famille d'objets numérotés : DOSSIER, CLIENT. Volontairement un texte et non un enum
    -- PostgreSQL : en ajouter une ne doit pas demander de migration.
    famille VARCHAR(20)  NOT NULL,
    annee   INTEGER      NOT NULL,
    -- Dernier numéro **servi**. Le prochain est celui-ci plus un.
    dernier INTEGER      NOT NULL DEFAULT 0,
    PRIMARY KEY (famille, annee),
    CONSTRAINT chk_compteur_reference_dernier CHECK (dernier >= 0)
);

COMMENT ON TABLE compteur_reference IS
    'Compteurs annuels des références attribuées par le système (DOS-2026-0001, CLI-2026-0001). '
    'Une ligne par famille et par année ; le numéro repart à 1 chaque 1er janvier.';

-- ─────────────────────────────────────────────────────────────────────────────
-- Reprise : ne jamais resservir un numéro déjà porté.
-- ─────────────────────────────────────────────────────────────────────────────
-- Les références saisies à la main ne suivent aucun format — l'environnement de recette en porte
-- deux, tapées au clavier. Mais rien n'interdit qu'une base, ici ou ailleurs, contienne déjà une
-- référence **de la forme attribuée**. Servir ce numéro une seconde fois échouerait sur la
-- contrainte d'unicité, et l'utilisateur lirait une erreur technique pour une création légitime.
--
-- Le compteur de chaque année démarre donc au plus grand numéro déjà porté cette année-là. Les
-- références d'un autre format sont ignorées : elles ne peuvent entrer en collision avec rien.

INSERT INTO compteur_reference (famille, annee, dernier)
SELECT 'DOSSIER',
       substring(reference from 5 for 4)::int,
       max(substring(reference from 10 for 4)::int)
  FROM dossier
 WHERE reference ~ '^DOS-[0-9]{4}-[0-9]{4}$'
 GROUP BY substring(reference from 5 for 4)::int;

INSERT INTO compteur_reference (famille, annee, dernier)
SELECT 'CLIENT',
       substring(reference from 5 for 4)::int,
       max(substring(reference from 10 for 4)::int)
  FROM client
 WHERE reference ~ '^CLI-[0-9]{4}-[0-9]{4}$'
 GROUP BY substring(reference from 5 for 4)::int;

-- ─────────────────────────────────────────────────────────────────────────────
-- Servir un numéro : une fonction, appelée par un SELECT.
-- ─────────────────────────────────────────────────────────────────────────────
-- Le code appelle `SELECT prochain_numero_reference(...)`, exactement comme V21 appelle
-- `SELECT nextval(...)`. Ce n'est pas un détail de style : Hibernate traite les requêtes natives
-- de modification autrement que les lectures, et un `INSERT … RETURNING` écrit dans le repository
-- aurait dépendu de la façon dont il les reconnaît. Une fonction rend l'appel indiscutablement
-- lisible côté Java, et laisse la partie atomique là où elle doit être : dans la base.
--
-- `ON CONFLICT DO UPDATE` verrouille la seule ligne de l'année en cours, le temps de l'incrément.
-- Deux créations simultanées obtiennent deux numéros différents sans que ni l'une ni l'autre
-- n'attende quoi que ce soit d'autre.
--
-- VOLATILE (le défaut) et non STABLE : la fonction modifie la base, et son résultat diffère à chaque
-- appel. L'annoncer autrement autoriserait PostgreSQL à n'en garder qu'un seul résultat par requête,
-- et deux dossiers créés dans la même transaction porteraient le même numéro.

CREATE FUNCTION prochain_numero_reference(p_famille VARCHAR, p_annee INTEGER)
RETURNS INTEGER AS $$
    INSERT INTO compteur_reference (famille, annee, dernier)
         VALUES (p_famille, p_annee, 1)
    ON CONFLICT (famille, annee)
    DO UPDATE SET dernier = compteur_reference.dernier + 1
      RETURNING dernier;
$$ LANGUAGE sql;

COMMENT ON FUNCTION prochain_numero_reference(VARCHAR, INTEGER) IS
    'Sert le numéro suivant pour une famille et une année, en créant le compteur au besoin. '
    'Atomique : deux appels simultanés ne peuvent pas obtenir le même numéro.';
