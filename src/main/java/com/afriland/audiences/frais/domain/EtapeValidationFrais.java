package com.afriland.audiences.frais.domain;

/**
 * Step checked by a {@code validation_frais} row: two individual steps (compliance check by the
 * assistant, appropriateness check by the DJA) and the two separate approvals of the joint
 * validation (DJ and DJA), tracked separately (US 4.5).
 */
public enum EtapeValidationFrais {
    CONFORMITE,
    OPPORTUNITE,
    CONJOINTE_DJ,
    CONJOINTE_DJA
}
