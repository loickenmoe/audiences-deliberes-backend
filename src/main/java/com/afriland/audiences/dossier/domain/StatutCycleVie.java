package com.afriland.audiences.dossier.domain;

import java.util.List;

/** Lifecycle status of a procedure step (RG-DOS-04). */
public enum StatutCycleVie {
    OUVERTURE,
    EN_COURS,
    EN_DELIBERE,
    DELIBERE_VIDE,
    CLOTURE,
    ARCHIVE;

    /**
     * Statuses of a step that is no longer active: no more hearings, no more appeals. Defined once
     * here so the notion cannot diverge across services when a status is added.
     */
    public static final List<StatutCycleVie> FERMES = List.of(CLOTURE, ARCHIVE);

    /** Whether this step is finished, i.e. nothing more is expected on it. */
    public boolean estFerme() {
        return FERMES.contains(this);
    }
}
