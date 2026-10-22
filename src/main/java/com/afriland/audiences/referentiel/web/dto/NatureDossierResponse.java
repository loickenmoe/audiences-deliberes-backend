package com.afriland.audiences.referentiel.web.dto;

import com.afriland.audiences.referentiel.domain.CategorieDossier;

/** Entry of the case file nature taxonomy (RG-DOS-02, Annex A), used by the creation form. */
public record NatureDossierResponse(Long id, String code, String libelle, CategorieDossier categorie) {
}
