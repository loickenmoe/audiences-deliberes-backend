package com.afriland.audiences.reporting.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Une audience passée dont le compte rendu du juriste n'a pas été saisi (écran 44).
 *
 * <p><b>D'où vient cette liste.</b> Le statut {@code TENUE} n'est posé qu'à la saisie du compte
 * rendu : une audience dont la date est passée et qui reste {@code PLANIFIEE} est donc, sans
 * ambiguïté et sans donnée nouvelle, une audience dont le compte rendu manque.
 *
 * <p><b>La journée en cours n'y figure jamais.</b> Une audience du jour n'est pas en retard, elle
 * est en cours : la borne haute s'arrête à hier, quelle que soit la fenêtre demandée.
 *
 * @param joursDeRetard         jours écoulés depuis la date de l'audience ; c'est l'ancienneté du
 *                              manquement, pas un délai réglementaire
 * @param compteRenduAvocatDepose l'avocat, lui, a rapporté. La distinction compte : elle sépare
 *                                « personne n'a rien dit de cette audience » de « la banque n'a pas
 *                                encore écrit ce que l'avocat a déjà rapporté »
 */
public record CompteRenduAttenduResponse(
        Long audienceId,
        Long dossierId,
        String referenceDossier,
        String juridiction,
        String etape,
        LocalDate datePlanifiee,
        long joursDeRetard,
        List<Long> juristesAffectes,
        boolean compteRenduAvocatDepose) {
}
