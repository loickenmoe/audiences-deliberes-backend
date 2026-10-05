-- Relance de l'avocat quand le compte rendu d'une audience passée manque (métier, 2026-10-05).
--
-- Ce que la Direction Juridique a demandé en présentation : « l'application doit alerter le
-- prestataire extérieur pour lui rappeler qu'il n'a pas fait son compte rendu lorsqu'une audience
-- est déjà passée et qu'il n'a pas encore déposé le sien ».
--
-- Ce qui existait, et ne suffisait pas : l'avocat reçoit des rappels AVANT l'audience, à J-7, J-3
-- et J-1 (RG-AUD-03). Après, plus rien. Une audience tenue dont le compte rendu n'arrive jamais ne
-- produisait aucun signal — ni vers l'avocat, ni vers personne.
--
-- Le délai est un paramètre, et non une constante du code, pour la même raison que
-- DELAI_VALIDATION_CONJOINTE : c'est un réglage de rythme de travail, qui se discute entre la
-- Direction Juridique et ses prestataires, et qui n'a pas à passer par une livraison.

INSERT INTO configuration_systeme (cle, valeur, description, modifiable_par) VALUES
    ('DELAI_RAPPEL_CR_AVOCAT', '2',
        'Nombre de jours après une audience passée au terme desquels l''avocat est relancé s''il '
        || 'n''a pas déposé son compte rendu, puis cadence des relances suivantes. RG-INT-11.',
        'ROLE_DJ');

-- La grille dit quel événement part sur quel canal. Un événement absent de toutes les grilles
-- n'est jamais envoyé : l'ajouter à la contrainte sans l'ajouter ici donnerait une relance que
-- personne ne reçoit, et le silence serait d'autant plus trompeur qu'il n'aurait l'air d'une panne.
--
-- V28 avait posé la règle : « grilles : tous les événements. Le DJ retire ce qu'il ne veut pas,
-- plutôt que de découvrir qu'un événement n'était jamais envoyé. » On la suit.
--
-- On n'ajoute qu'aux grilles qui portent déjà quelque chose : un canal que le DJ a vidé est un
-- canal qu'il a coupé, et le remplir d'un seul événement le rallumerait dans son dos.
UPDATE configuration_systeme
   SET valeur = valeur || ',COMPTE_RENDU_ATTENDU'
 WHERE cle IN ('GRILLE_NOTIFICATION_COURRIEL_PERSONNEL',
               'GRILLE_NOTIFICATION_SMS',
               'GRILLE_NOTIFICATION_WHATSAPP')
   AND valeur <> ''
   AND valeur NOT LIKE '%COMPTE_RENDU_ATTENDU%';
