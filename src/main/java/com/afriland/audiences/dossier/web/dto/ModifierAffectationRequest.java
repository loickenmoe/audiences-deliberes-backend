package com.afriland.audiences.dossier.web.dto;

import java.util.List;

/** FR-DOS-02, US 1.2. Listes complètes (remplacent l'affectation existante), pas un delta. */
public record ModifierAffectationRequest(List<Long> juristesAffectes, List<Long> avocatsAffectes) {
}
