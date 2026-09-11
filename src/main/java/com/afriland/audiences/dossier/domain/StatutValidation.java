package com.afriland.audiences.dossier.domain;

/**
 * Statut d'un circuit de validation à deux issues (approbation/rejet), partagé par la validation
 * de la sensibilité d'un dossier (RG-DOS-09, Q-10) et la dérogation de seuil (Q-29).
 */
public enum StatutValidation {
    EN_ATTENTE,
    VALIDEE,
    REJETEE
}
