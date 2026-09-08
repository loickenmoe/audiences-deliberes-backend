package com.afriland.audiences.referentiel.web.dto;

import com.afriland.audiences.referentiel.domain.CategorieDossier;

/** Entrée de la taxonomie des natures de dossier (RG-DOS-02, Annexe A) — alimente le formulaire de création. */
public record NatureDossierResponse(Long id, String code, String libelle, CategorieDossier categorie) {
}
