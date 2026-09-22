package com.afriland.audiences.portail.web.dto;

import com.afriland.audiences.audience.domain.StatutAudience;

import java.time.LocalDate;

/**
 * Une audience telle que l'avocat du dossier peut la voir (Q-96) : de quoi la désigner dans
 * {@code POST /publications/cr-audience} (US 5.1, « en choisissant le dossier et l'audience »).
 *
 * <p>Le compte rendu déjà saisi côté interne n'y figure pas : l'avocat dépose le sien, il n'a pas à
 * lire celui d'un juriste.
 */
public record AudienceAvocatResponse(
        Long id,
        LocalDate datePlanifiee,
        LocalDate dateTenue,
        StatutAudience statut) {
}
