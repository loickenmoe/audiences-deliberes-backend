-- Q-79 : une suppression directe (DJ/DJA) laissait EN_ATTENTE la demande de suppression du même
-- document — indécidable (document supprimé → 404) et relancée toutes les 72 h, sans fin. Le code
-- la ferme désormais au moment de la suppression ; cette migration ferme celles déjà orphelines.
-- Le validateur n'est pas connu après coup : valide_par reste nul, la date de décision est celle
-- de la migration.
UPDATE demande_suppression
   SET statut = 'APPROUVEE',
       date_decision = CURRENT_TIMESTAMP
 WHERE statut = 'EN_ATTENTE'
   AND document_id IN (SELECT id FROM document WHERE est_supprime = TRUE);
