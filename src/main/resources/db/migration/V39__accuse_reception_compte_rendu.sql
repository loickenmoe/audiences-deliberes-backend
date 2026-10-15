-- L'accusé de réception d'un compte rendu a son propre événement (métier, 2026-10-05).
--
-- Ce qui ne collait plus. Depuis RG-INT-06 amendée, l'Assistante n'arbitre plus un compte rendu :
-- elle en accuse réception. Mais l'avocat recevait toujours « Publication examinée - l'une de vos
-- publications a été examinée », texte de l'arbitrage, qui lui annonce une décision là où il n'y en
-- a pas eu. Un mot approximatif dans le seul message qu'il reçoit, et c'est lui qui le relève.
--
-- PUBLICATION_ARBITREE reste, pour ce qui s'arbitre vraiment : la validation et le rejet d'un
-- document du dossier.
--
-- Comme en V38, on n'ajoute qu'aux grilles qui portent déjà quelque chose : un canal que le DJ a
-- vidé est un canal qu'il a coupé, et le remplir d'un seul événement le rallumerait dans son dos.
UPDATE configuration_systeme
   SET valeur = valeur || ',COMPTE_RENDU_RECU'
 WHERE cle IN ('GRILLE_NOTIFICATION_COURRIEL_PERSONNEL',
               'GRILLE_NOTIFICATION_SMS',
               'GRILLE_NOTIFICATION_WHATSAPP')
   AND valeur <> ''
   AND valeur NOT LIKE '%COMPTE_RENDU_RECU%';
