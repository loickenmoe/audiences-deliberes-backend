package com.afriland.audiences.publication.web.dto;

import java.time.LocalDateTime;

/**
 * Une demande d'explication telle qu'un écran la lit (N7 temps 2).
 *
 * @param enAttente   déduit de {@code dateReponse}, jamais stocké : une colonne de plus pourrait
 *                    dire le contraire des deux autres, et c'est toujours elle qu'on croirait
 * @param dossierId   le dossier de la dernière audience manquée, s'il y en a un. Il situe la
 *                    demande sans la restreindre : elle porte sur une conduite, pas sur une affaire
 */
public record DemandeExplicationResponse(
        Long id,
        Long avocatId,
        String avocatNom,
        Long dossierId,
        String referenceDossier,
        String auteurNom,
        String contenu,
        LocalDateTime dateDemande,
        String reponse,
        LocalDateTime dateReponse,
        boolean enAttente) {
}
