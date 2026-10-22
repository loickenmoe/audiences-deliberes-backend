package com.afriland.audiences.alerte.domain;

/**
 * Alert types, including {@code ALARME} for {@code alarme} due dates. Must stay in sync with the
 * {@code chk_alerte_type} database constraint.
 */
public enum TypeAlerte {
    RAPPEL_AUDIENCE,
    DELAI_RECOURS,
    PROROGATION,
    ALARME,
    DOUBLON_AFFECTATION,
    DOUBLON_AUDIENCE,
    DOUBLON_FACTURE,
    NOUVELLE_DEMANDE_FRAIS,
    VALIDATION_CONJOINTE_REQUISE,
    PAIEMENT_EFFECTUE,
    NOUVELLE_PUBLICATION,
    DOCUMENT_CHARGE,
    SUPPRESSION_DOCUMENT,
    CONSTITUTION_A_SIGNER,
    CHARGE_MAX_AVOCAT,
    DELIBERE_ATTENDU,
    DOSSIER_INCOMPLET,
    /**
     * Two consecutive hearings without a report for the same lawyer (N7).
     * <p>Sent to the <b>Legal Director</b> only: they decide whether to request an explanation. The
     * application records the breach, it does not sanction it.
     * <p>Any new value requires a migration of {@code chk_alerte_type} (see V45).
     */
    MANQUEMENT_COMPTE_RENDU,

    /**
     * The request for explanation and its answer (N7, second step).
     * <p>One type for both directions: the request goes to the lawyer, the answer back to
     * management; it is the same thread and the alert text says which one.
     * <p>Any new value requires a migration of {@code chk_alerte_type} (see V46).
     */
    DEMANDE_EXPLICATION
}
