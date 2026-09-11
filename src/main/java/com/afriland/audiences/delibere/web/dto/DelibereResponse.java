package com.afriland.audiences.delibere.web.dto;

import com.afriland.audiences.delibere.domain.EtatDelibere;
import com.afriland.audiences.delibere.domain.ResultatDelibere;
import com.afriland.audiences.delibere.domain.StatutExpedition;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Q-78 : {@code etat} dit où en est le délibéré ({@code EN_ATTENTE} → prorogeable, rabattable,
 * vidable ; {@code VIDE} ; {@code RABATTU}). {@code resultat} et {@code statutExpedition} restent
 * nuls tant que le délibéré n'est pas vidé. {@code dateDeliberee} est la date annoncée, mise à jour
 * par chaque prorogation. {@code nombreProrogations} évite de relire l'historique pour l'afficher.
 */
public record DelibereResponse(
        Long id,
        Long dossierId,
        Long etapeId,
        LocalDate dateDeliberee,
        EtatDelibere etat,
        ResultatDelibere resultat,
        StatutExpedition statutExpedition,
        LocalDateTime dateEcheanceRecours,
        long nombreProrogations,
        LocalDate dateRabattement,
        String motifRabattement,
        LocalDateTime createdAt) {
}
