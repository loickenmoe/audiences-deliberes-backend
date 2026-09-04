package com.afriland.audiences.dossier.domain;

/** Type d'étape de procédure (RG-DOS-04). Un dossier porte 1 à 3 étapes. */
public enum TypeEtape {
    INSTANCE,
    RECOURS_1,
    RECOURS_2;

    /** Étape suivante ouverte automatiquement en cas d'exercice d'un recours (RG-DOS-04). */
    public TypeEtape suivante() {
        return switch (this) {
            case INSTANCE -> RECOURS_1;
            case RECOURS_1 -> RECOURS_2;
            case RECOURS_2 -> throw new IllegalStateException("Aucune étape suivante après RECOURS_2");
        };
    }
}
