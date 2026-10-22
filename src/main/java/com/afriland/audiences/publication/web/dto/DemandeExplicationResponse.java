package com.afriland.audiences.publication.web.dto;

import java.time.LocalDateTime;

/**
 * A request for explanation as read by the UI.
 *
 * @param enAttente derived from {@code dateReponse}, never stored
 * @param dossierId case file of the last missed hearing, if any; it places the request in context
 *                  without restricting it, since it concerns a conduct rather than a matter
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
