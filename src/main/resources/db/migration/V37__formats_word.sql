-- Les documents Word rejoignent les formats admis (métier, 2026-10-02).
--
-- Ce que le métier a demandé : « l'application ne prend pas en compte l'upload des fichiers Word,
-- tout comme les PDF et les images, on devrait également être capable de les uploader et de les
-- visualiser en cliquant sur l'œil ».
--
-- Le besoin est évident pour cette direction : une conclusion, un projet d'assignation, une note
-- d'audience circulent en Word avant d'exister en PDF. Les refuser obligeait à convertir chaque
-- pièce avant de la déposer — ou, en pratique, à ne pas la déposer du tout.
--
-- DEUX formats, deux usages distincts :
--
--   · DOCX — le format courant depuis 2007. Il est lisible DANS l'application : un composant le
--     rend à l'écran, fidèle à la mise en page de Word.
--
--   · DOC — l'ancien format binaire de Word 97-2003. Il s'accepte, parce qu'une banque en détient
--     encore ; il ne s'affiche PAS, aucune bibliothèque de navigateur ne sachant le rendre.
--     L'écran le dit franchement et propose le téléchargement, plutôt que de promettre un aperçu
--     qu'il ne tiendrait pas. Arbitrage du métier, 2026-10-02.
--
-- Le format reste dérivé de l'EXTENSION, pas du type MIME déclaré (Q-46) : un fichier renommé
-- passerait le contrôle. C'est la règle déjà en vigueur pour les cinq formats précédents, et la
-- changer ici seulement ferait diverger deux contrôles que le DJ pilote d'un seul paramètre.

ALTER TABLE document DROP CONSTRAINT chk_document_format;

ALTER TABLE document ADD CONSTRAINT chk_document_format
    CHECK (format IN ('PDF', 'PNG', 'JPG', 'TXT', 'XLSX', 'DOCX', 'DOC'));

-- La valeur de FORMATS_GED est ce que le DJ peut restreindre ensuite depuis l'écran de
-- configuration. On l'étend ici pour que les deux formats soient utilisables dès la migration :
-- les ajouter à la contrainte sans les ajouter au paramètre les laisserait refusés, et le refus
-- serait d'autant plus déroutant qu'il citerait un format désormais listé comme admis.
--
-- Le remplacement est conditionné à la valeur d'origine : si le DJ a déjà restreint la liste, son
-- choix est un arbitrage, pas un reliquat d'installation — on ne le défait pas dans son dos.
UPDATE configuration_systeme
   SET valeur = 'PDF,PNG,JPG,TXT,XLSX,DOCX,DOC',
       description = 'Formats de fichiers acceptés dans la GED. RG-GED-08. '
                     || 'Coordonné avec la configuration Spring (DSI). '
                     || 'DOCX et DOC ajoutés le 2026-10-02 ; seul DOCX s''affiche dans l''application.'
 WHERE cle = 'FORMATS_GED'
   AND valeur = 'PDF,PNG,JPG,TXT,XLSX';

COMMENT ON COLUMN document.format IS
    'Format déduit de l''extension du fichier déposé (RG-GED-04). DOCX et DOC admis depuis le '
    '2026-10-02 ; DOC ne dispose d''aucun aperçu dans l''application.';
