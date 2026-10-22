package com.afriland.audiences.dossier.domain;

/**
 * Status of a two-outcome validation workflow (approval/rejection), shared by case sensitivity
 * validation (RG-DOS-09) and threshold override.
 */
public enum StatutValidation {
    EN_ATTENTE,
    VALIDEE,
    REJETEE
}
