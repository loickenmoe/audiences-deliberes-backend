package com.afriland.audiences.delibere.web.dto;

import com.afriland.audiences.delibere.domain.EtatDelibere;
import com.afriland.audiences.delibere.domain.ResultatDelibere;
import com.afriland.audiences.delibere.domain.StatutExpedition;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * {@code etat} gives the deliberation state ({@code EN_ATTENTE}: can be extended, reopened or
 * delivered; {@code VIDE}; {@code RABATTU}). {@code resultat} and {@code statutExpedition} stay null
 * until the deliberation is delivered. {@code dateDeliberee} is the announced date, updated by each
 * extension. {@code nombreProrogations} avoids reloading the history to display it.
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
