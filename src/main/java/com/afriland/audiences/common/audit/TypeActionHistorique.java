package com.afriland.audiences.common.audit;

/**
 * Nature typée d'une action historisée (Q-111).
 *
 * <p><b>Le défaut qu'elle corrige.</b> {@code historique_action.action} était une phrase française
 * libre, parfois concaténée — « Modification de la configuration SEUIL_PROROGATIONS ». Filtrer sur
 * une telle colonne suppose un {@code LIKE '%…%'}, qui paraît commode et <b>ment par omission</b>
 * dès qu'un libellé change d'un mot : la ligne cesse d'être trouvée sans que rien ne le signale.
 * C'est le préalable à toute vue d'audit digne de ce nom (QF-24).
 *
 * <p><b>Le libellé est porté ici, pas à l'appel.</b> Les 42 points d'historisation ne transmettent
 * plus qu'un code ; le service en déduit la phrase. Un écart entre le code enregistré et le texte
 * affiché devient donc impossible — là où deux arguments côte à côte auraient fini par diverger.
 *
 * <p><b>Ce qui variait dans la phrase descend dans {@code details}.</b> Six libellés étaient
 * construits : le nom de l'étape, le sens d'une décision, la clé de configuration. Ces éléments sont
 * des <b>données</b> ; noyées dans de la prose, elles n'étaient pas interrogeables. Le libellé
 * devient générique et {@code details} porte la précision.
 */
public enum TypeActionHistorique {

    // ── Dossier ───────────────────────────────────────────────────────────────
    DOSSIER_CREE("Création du dossier"),
    DOSSIER_AFFECTATION_MODIFIEE("Modification de l'affectation"),
    /** {@code details} : {@code etape}, {@code ancienStatut}, {@code nouveauStatut}. */
    ETAPE_STATUT_MODIFIE("Changement de statut d'une étape"),
    /** {@code details} : {@code etape}, {@code etapeId}. Ouverture déclenchée par un recours. */
    ETAPE_OUVERTE_AUTOMATIQUEMENT("Ouverture automatique d'une étape (exercice d'un recours)"),
    /** {@code details} : {@code etape}, {@code etapeId}. Antécédents inconnus au moment de la saisie. */
    ETAPE_POSITIONNEE_DIRECTEMENT("Positionnement direct à une étape (antécédents inconnus)"),
    DEROGATION_SEUIL_DEMANDEE("Demande de dérogation de seuil"),
    /** {@code details} : {@code decision}, {@code auditId}, {@code motif}. */
    DEROGATION_SEUIL_ARBITREE("Décision sur la dérogation de seuil"),
    /** {@code details} : {@code decision}, {@code motifRejet}. */
    SENSIBILITE_ARBITREE("Décision sur la sensibilité du dossier"),

    // ── Audiences et alarmes ──────────────────────────────────────────────────
    AUDIENCE_PLANIFIEE("Planification d'une audience"),
    AUDIENCE_DOUBLON_CONFIRME("Doublon d'audience confirmé"),
    AUDIENCE_COMPTE_RENDU_ENREGISTRE("Compte rendu d'audience enregistré"),
    AUDIENCE_ANNULEE("Annulation d'une audience"),
    ALARME_CREEE("Création d'une alarme"),
    ALARME_REPROGRAMMEE("Reprogrammation d'une alarme"),
    ALARME_TRAITEE("Traitement d'une alarme"),

    // ── Délibérés ─────────────────────────────────────────────────────────────
    DELIBERE_MISE_EN_DELIBERE("Mise en délibéré"),
    DELIBERE_ENREGISTRE("Enregistrement d'un délibéré"),
    DELIBERE_VIDE("Délibéré vidé"),
    DELIBERE_PROROGE("Prorogation d'un délibéré"),
    DELIBERE_RABATTU("Rabattement du délibéré"),
    DELIBERE_EXPEDITION_MODIFIEE("Modification du statut d'expédition"),

    // ── Frais ─────────────────────────────────────────────────────────────────
    FRAIS_DEMANDE_DEPOSEE("Dépôt d'une demande de frais"),
    FRAIS_CONFORMITE_CONTROLEE("Contrôle de conformité d'une demande de frais"),
    FRAIS_OPPORTUNITE_CONTROLEE("Contrôle d'opportunité d'une demande de frais"),
    FRAIS_VALIDATION_CONJOINTE("Validation conjointe d'une demande de frais"),
    FRAIS_PAIEMENT_ENREGISTRE("Paiement d'une demande de frais"),

    // ── Gestion documentaire ──────────────────────────────────────────────────
    DOCUMENT_CHARGE("Chargement d'un document"),
    DOCUMENT_SUPPRESSION_DEMANDEE("Demande de suppression d'un document"),
    DOCUMENT_SUPPRIME_DIRECTEMENT("Suppression directe d'un document (DJ/DJA)"),
    DOCUMENT_SUPPRESSION_ARBITREE("Décision sur une demande de suppression de document"),

    // ── Constitutions de prestataires ─────────────────────────────────────────
    CONSTITUTION_DEMANDEE("Demande de constitution d'un prestataire"),
    CONSTITUTION_ARBITREE("Décision sur une demande de constitution"),
    /** Q-115 B2 : la lettre devient rédigeable, donc sa rédaction doit être traçable. */
    LETTRE_CONSTITUTION_REDIGEE("Rédaction de la lettre de constitution"),

    // ── Publications ──────────────────────────────────────────────────────────
    PUBLICATION_CR_DEPOSE("Dépôt d'un compte rendu d'audience"),
    PUBLICATION_AUTRE_DEPOSEE("Dépôt d'une autre publication"),
    PUBLICATION_ARBITREE("Décision sur une publication"),
    PUBLICATION_COMMENTEE("Commentaire sur une publication"),
    PUBLICATION_CORRESPONDANCE_TRANSMISE("Correspondance transmise à l'avocat"),

    // ── Décisions définitives et jurisprudence ────────────────────────────────
    ADJUDICATION_ENREGISTREE("Enregistrement d'une adjudication"),
    CONDAMNATION_ENREGISTREE("Enregistrement d'une condamnation pécuniaire"),
    CONDAMNATION_STATUT_MODIFIE("Mise à jour du statut d'une condamnation"),
    JURISPRUDENCE_INDEXEE("Indexation d'une jurisprudence"),

    // ── Administration (hors dossier) ─────────────────────────────────────────
    /** {@code details} : {@code cle}, {@code ancienneValeur}, {@code nouvelleValeur}. */
    CONFIGURATION_MODIFIEE("Modification de la configuration"),

    /**
     * Consultation de l'écran d'audit (Q-112). {@code details} porte les critères employés :
     * {@code perimetre}, et le cas échéant {@code auteurRecherche}, {@code dossierId},
     * {@code codeRecherche}, {@code dateDebut}, {@code dateFin}.
     *
     * <p>Le filtre par personne étant autorisé, cet écran permet de suivre nommément l'activité d'un
     * salarié. En tracer l'usage n'est pas une contrainte imposée au DJ et à la DJA : c'est ce qui
     * leur permet de <b>démontrer</b> qu'ils n'ont regardé que ce qu'ils devaient.
     */
    AUDIT_CONSULTE("Consultation de l'audit"),

    /**
     * Extraction de l'audit vers un fichier (Q-113). {@code details} porte la période, le format,
     * le nombre de lignes et l'empreinte du fichier produit.
     *
     * <p>Distinct de {@link #AUDIT_CONSULTE} : consulter laisse le contenu dans l'application,
     * exporter l'en fait sortir. C'est un acte plus lourd, qui mérite sa propre nature.
     */
    AUDIT_EXPORTE("Export de l'audit"),

    /**
     * Récupération d'une archive déjà produite (Q-113). {@code details} porte l'identifiant de
     * l'export et son empreinte.
     *
     * <p>Sans cette trace, l'archive serait un trou plus large que la table qu'elle double : on
     * pourrait y puiser indéfiniment sans que rien ne le dise.
     */
    ARCHIVE_TELECHARGEE("Téléchargement d'une archive d'audit"),

    /**
     * Lignes antérieures à la typification que la reprise n'a pas su rattacher.
     *
     * <p>Une valeur explicite plutôt que {@code NULL} : « on ne sait pas » se filtre, se compte et
     * se voit, là où un nul se serait glissé silencieusement hors de chaque requête. Aucune écriture
     * nouvelle ne l'emploie.
     */
    NON_CLASSEE("Action non classée");

    private final String libelle;

    TypeActionHistorique(String libelle) {
        this.libelle = libelle;
    }

    /** Phrase française affichée. Non traduisible en l'état (QF-24) — le code, lui, l'est. */
    public String libelle() {
        return libelle;
    }
}
