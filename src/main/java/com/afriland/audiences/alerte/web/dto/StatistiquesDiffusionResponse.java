package com.afriland.audiences.alerte.web.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Supervision du canal applicatif sur une fenêtre (Q-109, NF-ALR-02).
 *
 * <p><b>Deux taux distincts, et les confondre serait la première erreur.</b>
 *
 * <ul>
 *   <li>{@code tauxDelivrance} répond à l'exigence NF-ALR-02 : le canal a-t-il fait son travail ?
 *       Seuls les échecs comptent contre nous. Une alerte remise plus tard, parce que son
 *       destinataire n'était pas connecté, a bien été délivrée — le canal applicatif comprend la
 *       liste des notifications, alimentée inconditionnellement.</li>
 *   <li>{@code tauxDiffusionTempsReel} répond à une tout autre question : à quel point les gens
 *       sont-ils connectés quand on les alerte ? Un chiffre bas ici n'est pas un défaut du système,
 *       c'est une information sur les usages — et souvent une raison de brancher enfin un second
 *       canal. Un chiffre bas sur le premier taux, lui, est un défaut.</li>
 * </ul>
 *
 * <p>{@code observees} est le dénominateur réel : les alertes antérieures à la migration V16 n'ont
 * pas d'issue enregistrée et sont exclues. Le taux ne porte que sur ce qui a été mesuré, jamais sur
 * ce qu'on aurait pu supposer.
 *
 * <p>{@code nonTraitees} ne participe à aucun taux : c'est un signal d'un autre ordre — une alerte
 * techniquement délivrée que personne n'a ouverte. Elle dit quelque chose que la délivrance ne dit
 * pas, et la mélanger au taux brouillerait les deux.
 *
 * @param observees              alertes dont l'issue de diffusion a été enregistrée
 * @param diffusees              remises à une session vivante
 * @param differees              en attente dans la liste des notifications, destinataire déconnecté
 * @param echecs                 canal temps réel rompu — les seules pertes au sens de NF-ALR-02
 * @param nonTraitees            alertes de la fenêtre encore au statut {@code DECLENCHEE}
 * @param tauxDelivrance         {@code (observees - echecs) / observees}, en pourcentage
 * @param tauxDiffusionTempsReel {@code diffusees / observees}, en pourcentage
 * @param seuilRespecte          {@code tauxDelivrance >= 99}, le seuil que fixe NF-ALR-02
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

    /** Seuil de délivrance exigé par NF-ALR-02, en pourcentage. */
    public static final BigDecimal SEUIL_NF_ALR_02 = new BigDecimal("99");

    public static StatistiquesDiffusionResponse calculer(LocalDate dateDebut, LocalDate dateFin,
                                                            long diffusees, long differees, long echecs,
                                                            long nonTraitees) {
        long observees = diffusees + differees + echecs;
        BigDecimal tauxDelivrance = pourcentage(observees - echecs, observees);
        BigDecimal tauxTempsReel = pourcentage(diffusees, observees);
        // Sans aucune alerte observée, il n'y a rien à respecter ni à enfreindre : une fenêtre vide
        // ne vaut pas un manquement. On la déclare conforme plutôt que d'alarmer sur du vide.
        boolean respecte = observees == 0 || tauxDelivrance.compareTo(SEUIL_NF_ALR_02) >= 0;
        return new StatistiquesDiffusionResponse(dateDebut, dateFin, observees, diffusees, differees,
                echecs, nonTraitees, tauxDelivrance, tauxTempsReel, respecte);
    }

    /**
     * Deux décimales : à 99 %, un arrondi à l'entier ferait passer 98,6 % pour une conformité.
     * Le seuil est trop proche de 100 pour tolérer l'imprécision qui l'entoure.
     */
    private static BigDecimal pourcentage(long numerateur, long denominateur) {
        if (denominateur == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerateur)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominateur), 2, RoundingMode.HALF_UP);
    }
}
