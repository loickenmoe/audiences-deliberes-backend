-- La juridiction propre à chaque audience (observation métier du 2026-09-25).
--
-- Pourquoi elle manquait. Le dossier porte `juridiction_saisie` : la juridiction **saisie à
-- l'ouverture**, celle du premier degré. Le calendrier, ses exports et le journal des audiences
-- l'affichaient pour toutes les audiences du dossier — donc une audience d'appel était annoncée
-- devant le tribunal de première instance. L'information n'était pas absente : elle était fausse,
-- ce qui est pire, car rien n'invitait à la vérifier.
--
-- Une audience se tient devant une juridiction, qui change avec le degré : tribunal, cour d'appel,
-- Cour Suprême, CCJA. C'est donc à l'audience de la porter.
--
-- Les audiences existantes héritent de la juridiction de leur dossier : c'est ce qui était affiché
-- jusqu'ici, et c'est exact pour toutes celles de première instance — l'immense majorité. Les
-- corriger relève du métier, dossier par dossier ; inventer une autre valeur serait pire.

ALTER TABLE audience ADD COLUMN juridiction VARCHAR(100);

UPDATE audience a
   SET juridiction = d.juridiction_saisie
  FROM dossier d
 WHERE d.id = a.dossier_id
   AND a.juridiction IS NULL;

COMMENT ON COLUMN audience.juridiction IS
    'Juridiction devant laquelle l''audience se tient. Héritée du dossier à la planification, '
    'modifiable : elle change avec le degré (tribunal, cour d''appel, Cour Suprême, CCJA).';
