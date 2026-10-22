package com.afriland.audiences.common.audit;

/**
 * Typed kind of a recorded action. Audit filters use this code rather than the free-text label.
 *
 * <p>The label is derived from the code (callers only pass the code), so they cannot diverge.
 * Variable parts (step name, decision, configuration key) go into {@code details} as queryable data.
 */
public enum TypeActionHistorique {

    // Cases
    DOSSIER_CREE("Création du dossier"),
    DOSSIER_AFFECTATION_MODIFIEE("Modification de l'affectation"),
    /** {@code details}: {@code etape}, {@code ancienStatut}, {@code nouveauStatut}. */
    ETAPE_STATUT_MODIFIE("Changement de statut d'une étape"),
    /** {@code details}: {@code etape}, {@code etapeId}. Opened by the exercise of an appeal. */
    ETAPE_OUVERTE_AUTOMATIQUEMENT("Ouverture automatique d'une étape (exercice d'un recours)"),
    /** {@code details}: {@code etape}, {@code etapeId}. Prior history unknown at entry time. */
    ETAPE_POSITIONNEE_DIRECTEMENT("Positionnement direct à une étape (antécédents inconnus)"),
    DEROGATION_SEUIL_DEMANDEE("Demande de dérogation de seuil"),
    /** {@code details}: {@code decision}, {@code auditId}, {@code motif}. */
    DEROGATION_SEUIL_ARBITREE("Décision sur la dérogation de seuil"),
    /** {@code details}: {@code decision}, {@code motifRejet}. */
    SENSIBILITE_PROPOSEE("Proposition de diligence particulière"),
    SENSIBILITE_ARBITREE("Décision sur la sensibilité du dossier"),
    SENSIBILITE_RETIREE("Retrait de la diligence particulière"),

    // Hearings and alarms
    AUDIENCE_PLANIFIEE("Planification d'une audience"),
    AUDIENCE_DOUBLON_CONFIRME("Doublon d'audience confirmé"),
    AUDIENCE_COMPTE_RENDU_ENREGISTRE("Compte rendu d'audience enregistré"),
    AUDIENCE_ANNULEE("Annulation d'une audience"),
    ALARME_CREEE("Création d'une alarme"),
    ALARME_REPROGRAMMEE("Reprogrammation d'une alarme"),
    ALARME_TRAITEE("Traitement d'une alarme"),

    // Deliberations
    DELIBERE_MISE_EN_DELIBERE("Mise en délibéré"),
    DELIBERE_ENREGISTRE("Enregistrement d'un délibéré"),
    DELIBERE_VIDE("Délibéré vidé"),
    DELIBERE_PROROGE("Prorogation d'un délibéré"),
    DELIBERE_RABATTU("Rabattement du délibéré"),
    DELIBERE_EXPEDITION_MODIFIEE("Modification du statut d'expédition"),

    // Fees
    FRAIS_DEMANDE_DEPOSEE("Dépôt d'une demande de frais"),
    FRAIS_CONFORMITE_CONTROLEE("Contrôle de conformité d'une demande de frais"),
    FRAIS_OPPORTUNITE_CONTROLEE("Contrôle d'opportunité d'une demande de frais"),
    FRAIS_VALIDATION_CONJOINTE("Validation conjointe d'une demande de frais"),
    FRAIS_PAIEMENT_ENREGISTRE("Paiement d'une demande de frais"),

    // Document management
    DOCUMENT_CHARGE("Chargement d'un document"),
    DOCUMENT_SUPPRESSION_DEMANDEE("Demande de suppression d'un document"),
    DOCUMENT_SUPPRIME_DIRECTEMENT("Suppression directe d'un document (DJ/DJA)"),
    DOCUMENT_SUPPRESSION_ARBITREE("Décision sur une demande de suppression de document"),

    // Service provider appointments
    CONSTITUTION_DEMANDEE("Demande de constitution d'un prestataire"),
    CONSTITUTION_ARBITREE("Décision sur une demande de constitution"),
    /** The letter is editable, so its drafting must be traceable. */
    LETTRE_CONSTITUTION_REDIGEE("Rédaction de la lettre de constitution"),

    // Publications
    PUBLICATION_CR_DEPOSE("Dépôt d'un compte rendu d'audience"),
    PUBLICATION_AUTRE_DEPOSEE("Dépôt d'une autre publication"),
    PUBLICATION_ARBITREE("Décision sur une publication"),
    PUBLICATION_COMMENTEE("Commentaire sur une publication"),
    PUBLICATION_CORRESPONDANCE_TRANSMISE("Correspondance transmise à l'avocat"),

    // Final decisions and case law
    ADJUDICATION_ENREGISTREE("Enregistrement d'une adjudication"),
    CONDAMNATION_ENREGISTREE("Enregistrement d'une condamnation pécuniaire"),
    CONDAMNATION_STATUT_MODIFIE("Mise à jour du statut d'une condamnation"),
    JURISPRUDENCE_INDEXEE("Indexation d'une jurisprudence"),

    // Administration (outside any case)
    /** {@code details}: {@code cle}, {@code ancienneValeur}, {@code nouvelleValeur}. */
    CONFIGURATION_MODIFIEE("Modification de la configuration"),

    /**
     * Client update (outside any case). {@code details} holds the client {@code reference} and,
     * for each changed field only, its before/after values ({@code ancienNom} / {@code nouveauNom},
     * {@code ancienEmail} / {@code nouvelEmail}, {@code ancienTelephone} / {@code nouveauTelephone}).
     */
    CLIENT_MODIFIE("Modification d'un client"),

    /**
     * Stakeholder update (outside any case). {@code details} holds {@code prestataireId}, the current
     * name ({@code intervenant}) and before/after pairs of changed fields only (name, email, phone, and
     * {@code ancienneNotification} / {@code nouvelleNotification} for other providers). A lawyer's
     * Keycloak id is immutable and never appears.
     */
    INTERVENANT_MODIFIE("Modification d'un intervenant"),

    /**
     * Explanation request sent to a lawyer (e.g. two hearings without a report). {@code details}
     * holds {@code avocatId} and {@code demandeId}. A management decision, so its author is recorded.
     */
    EXPLICATION_DEMANDEE("Demande d'explication adressée à un avocat"),

    /**
     * Lawyer's answer to an explanation request. {@code details} holds {@code demandeId}. Closes the
     * exchange and releases the automatic reminder suspended by the request.
     */
    EXPLICATION_REPONDUE("Réponse à une demande d'explication"),

    /**
     * Audit screen consultation. {@code details} holds the criteria used: {@code perimetre} and, when
     * set, {@code auteurRecherche}, {@code dossierId}, {@code codeRecherche}, {@code dateDebut},
     * {@code dateFin}. Since filtering by person is allowed, recording usage lets DJ and DJA prove
     * they only looked at what they needed to.
     */
    AUDIT_CONSULTE("Consultation de l'audit"),

    /**
     * Audit export to a file. {@code details} holds the period, format, line count and file hash.
     * Distinct from {@link #AUDIT_CONSULTE}: exporting takes the content out of the application.
     */
    AUDIT_EXPORTE("Export de l'audit"),

    /**
     * Download of an existing audit archive. {@code details} holds the export id and its hash, so
     * archive downloads are never untraced.
     */
    ARCHIVE_TELECHARGEE("Téléchargement d'une archive d'audit"),

    /**
     * Document shared with the case lawyer, directly or by DJ/DJA on a sensitive case.
     * {@code details} holds {@code documentId}; records who let a document leave the bank, and when.
     */
    DOCUMENT_PARTAGE_AVOCAT("Document partagé avec l'avocat"),

    /**
     * Sensitive case: a legal officer proposes sharing, to be validated by DJ or DJA.
     * {@code details} holds {@code documentId}.
     */
    DOCUMENT_PARTAGE_PROPOSE("Partage d'un document proposé"),

    /**
     * DJ or DJA decides on a proposed share. {@code details} holds {@code documentId},
     * {@code decision} and, on rejection, {@code motif}.
     */
    DOCUMENT_PARTAGE_ARBITRE("Partage d'un document arbitré"),

    /**
     * A share withdrawn or a proposal cancelled. {@code details} holds {@code documentId} and
     * {@code ancienStatut}.
     */
    DOCUMENT_PARTAGE_RETIRE("Partage d'un document retiré"),

    /**
     * The lawyer opens or downloads a shared document. {@code details} holds {@code documentId} and
     * {@code avocatId}; shows whether the lawyer had seen it before the hearing.
     */
    DOCUMENT_CONSULTE_PAR_AVOCAT("Document consulté par l'avocat"),

    /**
     * A hearing's note to the lawyer written, changed or cleared. {@code details} holds
     * {@code audienceId}.
     */
    AUDIENCE_NOTE_AVOCAT_MODIFIEE("Note à l'avocat modifiée"),

    /**
     * Legacy rows that the migration could not map. An explicit value rather than {@code NULL}, so
     * "unknown" can be filtered and counted. Never used for new entries.
     */
    NON_CLASSEE("Action non classée");

    private final String libelle;

    TypeActionHistorique(String libelle) {
        this.libelle = libelle;
    }

    /** Displayed French label. */
    public String libelle() {
        return libelle;
    }
}
