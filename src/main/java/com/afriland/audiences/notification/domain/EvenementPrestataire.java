package com.afriland.audiences.notification.domain;

/**
 * Événements notifiés à un prestataire par un canal externe (décision D19 de
 * NOTIFICATION_ENGINE_PLAN.md). Les cinq premiers existent déjà comme alertes applicatives
 * réservées à l'avocat qui a un compte ; les quatre suivants ont été ajoutés par le porteur du projet.
 * <p>
 * Distinct de {@link com.afriland.audiences.alerte.domain.TypeAlerte} : un prestataire n'a pas
 * forcément de compte applicatif, et ces événements ne passent donc pas tous par une alerte.
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
     * Une audience est passée et l'avocat n'a toujours pas déposé son compte rendu
     * (RG-INT-11, métier du 2026-10-05). Relancé tous les {@code DELAI_RAPPEL_CR_AVOCAT}
     * jours jusqu'au dépôt.
     */
    COMPTE_RENDU_ATTENDU
}
