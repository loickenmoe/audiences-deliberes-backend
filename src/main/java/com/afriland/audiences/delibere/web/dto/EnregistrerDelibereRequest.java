package com.afriland.audiences.delibere.web.dto;

import com.afriland.audiences.delibere.domain.ResultatDelibere;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Q-78 : sans {@code resultat}, c'est une <b>mise en délibéré</b> — {@code dateDeliberee} est la date
 * annoncée par le tribunal, l'étape reste {@code EN_DELIBERE}, et aucune échéance de recours n'est
 * admise. Avec {@code resultat}, le délibéré déjà rendu est enregistré et vidé d'un même geste :
 * {@code dateEcheanceRecours} requise et postérieure à {@code dateDeliberee} si {@code resultat}
 * ∈ {DEFAVORABLE, MIXTE} (voie de recours ouverte, Q-42) ; absente sinon (RG-DEL-01).
 */
public record EnregistrerDelibereRequest(
        @NotNull Long etapeId,
        @NotNull LocalDate dateDeliberee,
        ResultatDelibere resultat,
        LocalDateTime dateEcheanceRecours) {
}
