package com.afriland.audiences.frais.domain;

/**
 * Étape contrôlée par une ligne {@code validation_frais} (Q-05) : deux étapes individuelles
 * (contrôle de conformité par l'Assistante, contrôle d'opportunité par le DJA) et deux accords
 * distincts de la validation conjointe (DJ et DJA), tracés séparément (US 4.5).
 */
public enum EtapeValidationFrais {
    CONFORMITE,
    OPPORTUNITE,
    CONJOINTE_DJ,
    CONJOINTE_DJA
}
