package com.afriland.audiences.referentiel.domain;

/**
 * Catégorie d'un dossier judiciaire (RG-DOS-02). Détermine les champs conditionnels obligatoires
 * (dossier de crédit / PV de transfert vs incident de compte / éléments justificatifs) — cf.
 * DECISIONS_AND_OPEN_QUESTIONS.md Q-11 pour la cardinalité exacte (ET/OU), non tranchée en M2.
 */
public enum CategorieDossier {
    RECOUVREMENT,
    EXPLOITATION_LITIGES
}
