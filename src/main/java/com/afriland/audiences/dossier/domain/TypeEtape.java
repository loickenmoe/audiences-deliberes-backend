package com.afriland.audiences.dossier.domain;

/**
 * Procedure step type (RG-DOS-04). A case holds 1 to 4 steps.
 * <p><b>The two cassation appeals are concurrent, not successive</b>: a case can be brought before
 * the Supreme Court <b>and</b> the CCJA (OHADA) at the same time. {@link #RECOURS_2_CCJA} does not
 * follow {@link #RECOURS_2}; both are terminal.
 * <p><b>The order of values matters.</b> Steps are sorted alphabetically on the {@code type} column
 * ({@code findByDossierIdOrderByTypeAsc}, {@code @Enumerated(STRING)}), which drives display order
 * and the current step. {@code 'RECOURS_2_CCJA'} sorts after {@code 'RECOURS_2'}; a name like
 * {@code CCJA} would silently sort before {@code INSTANCE}.
 */
public enum TypeEtape {
    INSTANCE,
    RECOURS_1,
    RECOURS_2,
    RECOURS_2_CCJA;

    /**
     * Next step opened automatically when an appeal is lodged (RG-DOS-04). The CCJA appeal is not
     * included: it is a decision, opened by direct positioning.
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
     * True when no appeal follows this step in the application. Exposed here rather than checked by
     * callers, since there is more than one terminal step.
     */
    public boolean estTerminale() {
        return this == RECOURS_2 || this == RECOURS_2_CCJA;
    }
}
