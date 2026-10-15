-- Une nature de plus : le contentieux en responsabilité civile (métier, 2026-10-03, observation 7).
--
-- La Direction Juridique veut le COMPTER À PART, et non le fondre dans « Civil et commercial » qui
-- existe déjà dans la même catégorie. C'est le point qui justifie une ligne propre : deux natures
-- voisines ne se valent que si l'on veut les distinguer dans les rapports. Arbitrage confirmé le
-- 2026-10-05.
--
-- Rien d'autre ne change : la catégorie reste EXPLOITATION_LITIGES, donc les champs conditionnels
-- du formulaire de création (incident de compte, éléments justificatifs) et les pièces exigées
-- restent ceux de cette catégorie.

INSERT INTO nature_dossier (code, libelle, categorie) VALUES
    ('RESPONSABILITE_CIVILE', 'Contentieux en responsabilité civile', 'EXPLOITATION_LITIGES')
ON CONFLICT (code) DO NOTHING;
