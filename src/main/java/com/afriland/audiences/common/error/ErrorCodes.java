package com.afriland.audiences.common.error;

/**
 * Codes d'erreur applicatifs. Les codes {@code ERR-0xx} reprennent l'annexe du contrat d'API
 * (REF-07 §6.1) ; les codes {@code ERR-*} additionnels couvrent les cas non numérotés par le
 * contrat (validation générique, ressource introuvable, droits insuffisants, erreur interne).
 * <p>
 * {@code ERR-007} (plus de 10 prorogations) n'apparaît pas ici : ce n'est pas un cas d'erreur —
 * la prorogation est enregistrée avec succès (201) et un champ {@code alerte: true} est ajouté
 * au corps de la réponse (cf. DECISIONS_AND_OPEN_QUESTIONS.md Q-17). Il sera documenté au niveau
 * du DTO de réponse du module délibéré (M7), pas ici.
 */
public final class ErrorCodes {

    private ErrorCodes() {
    }

    // Codes numérotés par le contrat d'API (REF-07 §6.1)
    public static final String METADONNEES_MANQUANTES = "ERR-001";
    public static final String REFERENCE_DOSSIER_DUPLIQUEE = "ERR-002";
    public static final String DOUBLON_AFFECTATION = "ERR-003";
    public static final String TRANSITION_NON_AUTORISEE = "ERR-004";
    public static final String DOUBLON_AUDIENCE = "ERR-005";
    public static final String FACTURE_EN_DOUBLE = "ERR-006";
    public static final String DOCUMENT_NON_CONFORME = "ERR-008";
    public static final String ACCES_RESTREINT_PUBLICATION = "ERR-009";

    // Codes génériques, pour les cas non couverts par un ERR-0xx du contrat
    public static final String REQUETE_INVALIDE = "ERR-VALIDATION";
    public static final String NON_AUTHENTIFIE = "ERR-UNAUTHENTICATED";
    public static final String DROITS_INSUFFISANTS = "ERR-FORBIDDEN";
    /**
     * Le compte connecté porte {@code ROLE_AVOCAT} mais **aucun avocat du référentiel** ne lui est
     * rattaché (Q-107).
     *
     * <p>Ce n'est pas un refus de droits : c'est un état d'inscription. Un avocat existe pour le
     * métier parce qu'un juriste l'a inscrit au répertoire avec son identifiant de connexion
     * (RG-INT-01, UC-INT-01) — jamais parce qu'il possède un compte. Tant que cette fiche n'existe
     * pas, le portail n'a rien à lui montrer, et il n'y peut rien lui-même.
     *
     * <p>Le distinguer de {@link #DROITS_INSUFFISANTS} permet à l'interface de l'expliquer au lieu
     * d'afficher « le chargement a échoué » : le premier appelle un message, le second une
     * correction. Même raison d'être que {@link #ACCES_RESTREINT_PUBLICATION} (ERR-009), qui isole
     * déjà un état légitime d'un vrai refus.
     */
    public static final String AVOCAT_NON_RATTACHE = "ERR-AVOCAT-NON-RATTACHE";
    public static final String RESSOURCE_INTROUVABLE = "ERR-NOT-FOUND";
    /**
     * Conflit générique. Employé par une dizaine de cas sans rapport entre eux — référence client
     * déjà prise, compte déjà rattaché, étape déjà ouverte, accord déjà enregistré, publication
     * déjà traitée… Le code seul ne dit donc rien du champ à corriger : lorsqu'un champ est en
     * cause, lever l'exception avec {@link ApiException#conflictSurChamp} pour le nommer dans la
     * réponse. Les conflits d'état (« cette demande a déjà été traitée ») n'en visent aucun et
     * restent sur {@link ApiException#conflict}.
     */
    public static final String CONFLIT = "ERR-CONFLICT";
    public static final String REGLE_METIER_VIOLEE = "ERR-BUSINESS-RULE";
    public static final String ERREUR_INTERNE = "ERR-INTERNAL";
}
