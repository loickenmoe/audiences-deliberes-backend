package com.afriland.audiences.common.error;

/**
 * Application error codes. {@code ERR-0xx} codes come from the API contract appendix (REF-07 §6.1);
 * the other {@code ERR-*} codes cover generic cases (validation, not found, forbidden, internal).
 * <p>
 * {@code ERR-007} (more than 10 extensions) is not an error: the extension is saved (201) and the
 * response carries {@code alerte: true}.
 */
public final class ErrorCodes {

    private ErrorCodes() {
    }

    // Codes numbered by the API contract (REF-07 §6.1)
    public static final String METADONNEES_MANQUANTES = "ERR-001";
    public static final String REFERENCE_DOSSIER_DUPLIQUEE = "ERR-002";
    public static final String DOUBLON_AFFECTATION = "ERR-003";
    public static final String TRANSITION_NON_AUTORISEE = "ERR-004";
    public static final String DOUBLON_AUDIENCE = "ERR-005";
    public static final String FACTURE_EN_DOUBLE = "ERR-006";
    public static final String DOCUMENT_NON_CONFORME = "ERR-008";
    public static final String ACCES_RESTREINT_PUBLICATION = "ERR-009";

    // Generic codes for cases not covered by a contract ERR-0xx
    public static final String REQUETE_INVALIDE = "ERR-VALIDATION";
    public static final String NON_AUTHENTIFIE = "ERR-UNAUTHENTICATED";
    public static final String DROITS_INSUFFISANTS = "ERR-FORBIDDEN";
    /**
     * The account has {@code ROLE_AVOCAT} but no lawyer in the directory is linked to it. This is a
     * registration state, not a permission denial: a lawyer exists once a legal officer registers
     * them with their login (RG-INT-01, UC-INT-01). Distinct from {@link #DROITS_INSUFFISANTS} so the
     * UI can explain it, like {@link #ACCES_RESTREINT_PUBLICATION} (ERR-009).
     */
    public static final String AVOCAT_NON_RATTACHE = "ERR-AVOCAT-NON-RATTACHE";
    public static final String RESSOURCE_INTROUVABLE = "ERR-NOT-FOUND";
    /**
     * Generic conflict, shared by many unrelated cases. When a field is at fault, raise it with
     * {@link ApiException#conflictSurChamp} to name the field; state conflicts ("already processed")
     * stay on {@link ApiException#conflict}.
     */
    public static final String CONFLIT = "ERR-CONFLICT";
    public static final String REGLE_METIER_VIOLEE = "ERR-BUSINESS-RULE";
    public static final String ERREUR_INTERNE = "ERR-INTERNAL";
}
