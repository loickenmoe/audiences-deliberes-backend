package com.afriland.audiences.dossier.domain;

/**
 * Type d'étape de procédure (RG-DOS-04). Un dossier porte 1 à 4 étapes.
 *
 * <p><b>Les deux pourvois sont concurrents, pas successifs.</b> La Direction Juridique a confirmé
 * (2026-09-25) qu'un même dossier peut être porté devant la Cour Suprême <b>et</b> devant la CCJA
 * en même temps : ce sont deux ordres juridictionnels — national et supranational OHADA — saisis
 * par des pourvois distincts. {@link #RECOURS_2_CCJA} ne vient donc pas après {@link #RECOURS_2} ;
 * il est à côté, et tous deux sont terminaux.
 *
 * <p><b>L'ordre des valeurs n'est pas décoratif.</b> Les étapes d'un dossier sont triées
 * alphabétiquement sur la colonne {@code type} ({@code findByDossierIdOrderByTypeAsc}, avec
 * {@code @Enumerated(STRING)}), et c'est ce tri qui décide de l'ordre d'affichage comme du calcul
 * de l'étape courante. {@code 'RECOURS_2_CCJA'} suit {@code 'RECOURS_2'} : l'ordre procédural est
 * préservé. Une valeur nommée {@code CCJA} ou {@code CASSATION} se serait rangée <b>avant</b>
 * {@code INSTANCE}, sans que rien n'échoue.
 */
public enum TypeEtape {
    INSTANCE,
    RECOURS_1,
    RECOURS_2,
    RECOURS_2_CCJA;

    /**
     * Étape suivante ouverte automatiquement en cas d'exercice d'un recours (RG-DOS-04).
     *
     * <p>Le pourvoi CCJA n'y figure pas : il ne suit mécaniquement aucune étape, il se décide. Il
     * s'ouvre par positionnement direct, comme toute étape que la procédure n'impose pas.
     */
    public TypeEtape suivante() {
        return switch (this) {
            case INSTANCE -> RECOURS_1;
            case RECOURS_1 -> RECOURS_2;
            case RECOURS_2, RECOURS_2_CCJA ->
                    throw new IllegalStateException("Aucune étape suivante après " + this);
        };
    }

    /**
     * Vrai lorsque aucune voie de recours ne suit cette étape dans l'application.
     *
     * <p>Exposé ici plutôt que testé chez l'appelant : un {@code == RECOURS_2} disséminé dans les
     * services aurait fallu le jour où une seconde étape terminale est apparue — ce qui vient
     * précisément d'arriver avec la CCJA.
     */
    public boolean estTerminale() {
        return this == RECOURS_2 || this == RECOURS_2_CCJA;
    }
}
