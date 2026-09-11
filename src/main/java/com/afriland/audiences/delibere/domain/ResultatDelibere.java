package com.afriland.audiences.delibere.domain;

/** Résultat d'un délibéré (RG-DEL-01). Seuls {@code DEFAVORABLE} et {@code MIXTE} ouvrent une voie de recours (Q-42). */
public enum ResultatDelibere {
    FAVORABLE,
    DEFAVORABLE,
    MIXTE
}
