package com.afriland.audiences.notification.domain;

/**
 * Events notified to a provider through an external channel. The first five also exist as in-app
 * alerts for lawyers with an account.
 * <p>
 * Distinct from {@link com.afriland.audiences.alerte.domain.TypeAlerte}: a provider may have no
 * application account, so not all these events go through an alert.
 */
public enum EvenementPrestataire {
    CONSTITUTION_VALIDEE,
    FRAIS_REJETES,
    PAIEMENT_EFFECTUE,
    PUBLICATION_ARBITREE,
    CORRESPONDANCE_TRANSMISE,
    RAPPEL_AUDIENCE,
    AFFECTATION_DOSSIER,
    DELAI_RECOURS,
    SUPPRESSION_DOCUMENT_ARBITREE,

    /**
     * A hearing has passed and the lawyer has not filed the report yet (RG-INT-11). Repeated every
     * {@code DELAI_RAPPEL_CR_AVOCAT} days until filed.
     */
    COMPTE_RENDU_ATTENDU,

    /**
     * Management asks the lawyer for an explanation. Unlike the automatic {@link #COMPTE_RENDU_ATTENDU}
     * reminder, this one is written by a person and expects a reply.
     */
    DEMANDE_EXPLICATION,

    /**
     * The assistant acknowledged receipt of a report (RG-INT-06). Distinct from
     * {@link #PUBLICATION_ARBITREE}, which announces a decision on the document; reports receive no
     * such decision.
     */
    COMPTE_RENDU_RECU
}
