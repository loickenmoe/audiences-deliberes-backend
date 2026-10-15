package com.afriland.audiences.dossier.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Pose ou retrait direct de la diligence particulière par le DJ ou la DJA (métier, 2026-10-03,
 * observation 5 ; les deux sens arrêtés le 2026-10-06).
 *
 * <p><b>Sans proposition.</b> Le juriste propose et attend un arbitrage ; le DJ et la DJA, eux,
 * décident - leur faire passer par une proposition qu'ils arbitreraient eux-mêmes n'ajouterait
 * qu'une étape sans contrôle.
 *
 * <p>{@code estSensible} à vrai exige {@code seuilMontant} et {@code typeClientSensible}, comme
 * toute diligence validée. À faux, c'est un retrait, et le {@code motif} devient obligatoire : un
 * dossier qui cesse d'être suivi en diligence particulière sort du circuit de double validation des
 * frais, et l'audit demandera pourquoi.
 */
public record ModifierSensibiliteRequest(
        @NotNull Boolean estSensible,
        @PositiveOrZero Double seuilMontant,
        @Size(max = 50) String typeClientSensible,
        @Size(max = 255) String motif) {
}
