-- Q-110 : rendre lisibles les actions historisées hors dossier.
--
-- RG-DOS-06 historise chaque modification, mais le seul point de lecture est
-- `GET /dossiers/{id}/historique` — rattaché à un dossier. Les actions qui n'en ont pas étaient donc
-- **écrites et jamais relues** : modifications de paramètres système et indexations de jurisprudence
-- sans dossier. Le manque s'est aggravé avec Q-108, qui a doublé le nombre de détenteurs du pouvoir
-- de modifier les paramètres sans qu'aucun écran ne permette d'en relire l'exercice.
--
-- L'index est **partiel** : la requête ne porte jamais que sur `dossier_id IS NULL`, une fraction
-- des lignes. Un index complet indexerait tout l'historique — celui des dossiers compris, qui ne
-- sera jamais interrogé par ce chemin — pour un gain nul.
--
-- Il reproduit l'ordre de lecture, du plus récent au plus ancien, départagé par `id` : sans ce
-- second critère, deux actions du même instant peuvent changer de place d'une page à l'autre et une
-- ligne se perdre entre deux pages (même raison qu'en Q-92/Q-93 pour les listes paginées).
CREATE INDEX idx_historique_hors_dossier_tri
    ON historique_action(date_action DESC, id DESC)
    WHERE dossier_id IS NULL;

-- Le filtre par auteur est le premier que l'on ajoute quand on cherche « qui a changé ce seuil ».
CREATE INDEX idx_historique_hors_dossier_auteur
    ON historique_action(utilisateur_id, date_action DESC)
    WHERE dossier_id IS NULL;
