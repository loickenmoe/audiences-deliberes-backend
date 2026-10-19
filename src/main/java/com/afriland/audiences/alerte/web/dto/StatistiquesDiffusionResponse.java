package com.afriland.audiences.alerte.web.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * In-app channel monitoring over a window (NF-ALR-02).
 * <p>{@code tauxDelivrance} answers NF-ALR-02 (did the channel do its job?): only failures count, a
 * deferred alert is delivered. {@code tauxDiffusionTempsReel} only reflects how often recipients
 * are connected; a low value is not a system fault.
 * <p>{@code nonTraitees} is not part of any rate: it signals alerts delivered but never opened.
 *
 * @param observees              alerts with a recorded delivery outcome (pre-V16 alerts excluded)
 * @param diffusees              delivered to a live session
 * @param differees              waiting in the notification list, recipient offline
 * @param echecs                 real-time channel broken; the only losses under NF-ALR-02
 * @param nonTraitees            alerts of the window still {@code DECLENCHEE}
 * @param tauxDelivrance         {@code (observees - echecs) / observees}, as a percentage
 * @param tauxDiffusionTempsReel {@code diffusees / observees}, as a percentage
 * @param seuilRespecte          {@code tauxDelivrance >= 99}, the NF-ALR-02 threshold
 */
public record StatistiquesDiffusionResponse(
        LocalDate dateDebut,
        LocalDate dateFin,
        long observees,
        long diffusees,
        long differees,
        long echecs,
        long nonTraitees,
        BigDecimal tauxDelivrance,
        BigDecimal tauxDiffusionTempsReel,
        boolean seuilRespecte) {

    /** Delivery threshold required by NF-ALR-02, as a percentage. */
    public static final BigDecimal SEUIL_NF_ALR_02 = new BigDecimal("99");

    public static StatistiquesDiffusionResponse calculer(LocalDate dateDebut, LocalDate dateFin,
                                                            long diffusees, long differees, long echecs,
                                                            long nonTraitees) {
        long observees = diffusees + differees + echecs;
        BigDecimal tauxDelivrance = pourcentage(observees - echecs, observees);
        BigDecimal tauxTempsReel = pourcentage(diffusees, observees);
        // With no observed alert there is nothing to breach: an empty window is compliant.
        boolean respecte = observees == 0 || tauxDelivrance.compareTo(SEUIL_NF_ALR_02) >= 0;
        return new StatistiquesDiffusionResponse(dateDebut, dateFin, observees, diffusees, differees,
                echecs, nonTraitees, tauxDelivrance, tauxTempsReel, respecte);
    }

    /** Two decimals: rounding to an integer would let 98.6 % pass as compliant. */
    private static BigDecimal pourcentage(long numerateur, long denominateur) {
        if (denominateur == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerateur)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominateur), 2, RoundingMode.HALF_UP);
    }
}
