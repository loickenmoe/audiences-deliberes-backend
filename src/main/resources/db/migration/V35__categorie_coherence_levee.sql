-- Les références conditionnelles par catégorie cessent d'être obligatoires (métier, 2026-09-29).
--
-- Ce que le métier a dit : « sur le formulaire de création d'un dossier, ce sont les pièces jointes
-- (dossier crédit d'origine et PV de transfert) qui sont obligatoires, pas leur texte associé ».
-- L'obligation ne disparaît pas, elle change d'objet : du champ texte vers le fichier.
--
-- Constat qui l'a motivée : sur les 254 dossiers de l'environnement de recette, les deux références
-- étaient remplies partout — elles étaient exigées — mais 51 seulement portaient un document. Le
-- système réclamait le nom de la pièce et se passait de la pièce.
--
-- `chk_categorie_coherence` (V1, reprise de REF-05 §12.1) exigeait au moins l'une des deux
-- références selon la catégorie. Elle était le dernier filet d'une règle qui n'a plus cours :
-- maintenue, elle ferait échouer en erreur technique une création que le métier veut permettre.
--
-- Ce que cette migration ne fait PAS : exiger les fichiers en base. Une pièce se dépose en GED
-- après la création, qui seule produit l'identifiant du dossier ; aucune contrainte ne peut réclamer
-- une ligne qui n'existera qu'ensuite. L'obligation est tenue par l'écran, limite acceptée par le
-- métier le même jour (l'alternative étant de refondre la création pour que le dossier naisse avec
-- ses pièces).
--
-- Les colonnes sont conservées : les 254 dossiers existants gardent leurs références, qui restent
-- saisissables et servent à retrouver un dossier.

ALTER TABLE dossier DROP CONSTRAINT chk_categorie_coherence;

COMMENT ON COLUMN dossier.dossier_credit_origine IS
    'Référence du dossier de crédit d''origine (recouvrement). Facultative depuis le 2026-09-29 : '
    'l''obligation porte sur la pièce jointe, tenue par l''écran de création.';

COMMENT ON COLUMN dossier.pv_transfert IS
    'Référence du PV de transfert (recouvrement). Facultative depuis le 2026-09-29 : '
    'l''obligation porte sur la pièce jointe, tenue par l''écran de création.';

COMMENT ON COLUMN dossier.incident_compte_reference IS
    'Référence de l''incident de compte (exploitation/litiges). Facultative depuis le 2026-09-29 : '
    'l''obligation porte sur la pièce jointe, tenue par l''écran de création.';

COMMENT ON COLUMN dossier.elements_justificatifs IS
    'Éléments justificatifs (exploitation/litiges). Facultatifs depuis le 2026-09-29 : '
    'l''obligation porte sur la pièce jointe, tenue par l''écran de création.';
