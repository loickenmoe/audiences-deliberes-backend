package com.afriland.audiences.intervenant.web.dto;

/**
 * Un avocat du répertoire, avec ce qui sert à lui confier un dossier (RG-INT-01).
 *
 * @param indicateurCharge       nombre de dossiers actifs affectés.
 * @param deliberesFavorables    délibérés rendus en faveur de la banque sur ses dossiers.
 * @param deliberesTotal         délibérés rendus, tous sens confondus. <b>Zéro n'est pas une
 *                               contre-performance</b> : c'est l'absence de mesure, et l'écran le
 *                               dit plutôt que d'afficher un chiffre qui se lirait comme un échec.
 * @param indicateurPerformance  le taux correspondant, entre 0 et 1.
 *                               <p>
 *                               <b>Plus affiché depuis le 2026-10-07</b> : « le métier veut les
 *                               chiffres, pas les pourcentages » (2026-10-06). Les deux décomptes
 *                               ci-dessus disent ce qu'il résumait, et disent en plus <b>sur
 *                               combien d'affaires</b> il porte - un taux de 100 % sur un seul
 *                               délibéré ne vaut pas le même jugement qu'un taux de 70 % sur trente.
 *                               <p>
 *                               Il reste dans le contrat : la même requête le produit en comptant
 *                               les résultats, et le retirer serait une rupture pour un
 *                               consommateur qui le lirait encore. C'est le choix déjà fait pour
 *                               {@code tauxSuccesRecouvrement} du rapport périodique.
 */
public record AvocatRepertoireResponse(
        Long id,
        String nom,
        String email,
        String telephone,
        long indicateurCharge,
        long deliberesFavorables,
        long deliberesTotal,
        double indicateurPerformance) {
}
