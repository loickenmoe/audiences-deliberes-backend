package com.afriland.audiences.alerte.domain;

/**
 * Types d'alerte — liste réunie retenue en Q-22 (DECISIONS_AND_OPEN_QUESTIONS.md), complétée de
 * {@code ALARME} pour les échéances de la table {@code alarme} (Q-04). Doit rester synchronisée
 * avec la contrainte {@code chk_alerte_type} de {@code V1__schema_initial.sql}.
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
     * Deux audiences consecutives sans compte rendu, pour un meme avocat (N7, metier du 2026-10-07).
     *
     * <p>Elle va au <b>Directeur Juridique</b>, et a lui seul : c'est lui qui decide d'envoyer une
     * demande d'explication. L'application constate un manquement, elle ne le sanctionne pas.
     *
     * <p>Toute valeur ajoutee ici exige une migration de {@code chk_alerte_type} - voir V45.
     */
    MANQUEMENT_COMPTE_RENDU,

    /**
     * La demande d'explication, et la reponse qui revient (N7 temps 2, metier du 2026-10-07).
     *
     * <p><b>Un seul type pour les deux sens.</b> La demande part vers l'avocat, la reponse revient
     * vers la direction : c'est le meme fil, et le texte de l'alerte dit lequel. Deux types
     * auraient double la grille de notification pour une distinction que le destinataire fait deja
     * de lui-meme - il sait s'il a ecrit ou s'il attend.
     *
     * <p>Toute valeur ajoutee ici exige une migration de {@code chk_alerte_type} - voir V46.
     */
    DEMANDE_EXPLICATION
}
