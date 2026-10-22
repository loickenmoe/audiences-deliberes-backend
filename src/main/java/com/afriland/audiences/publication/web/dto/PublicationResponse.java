package com.afriland.audiences.publication.web.dto;

import com.afriland.audiences.publication.domain.StatutPublication;
import com.afriland.audiences.publication.domain.TypeAutrePublication;
import com.afriland.audiences.publication.domain.TypePublication;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Flattened view of {@link com.afriland.audiences.publication.domain.Publication}: subtype-specific
 * fields ({@code audienceId}/{@code audienceDate}/{@code contenu} for a hearing report,
 * {@code typeDocument}/{@code nomFichier} for an other publication) are {@code null} for the other
 * subtype.
 * <p>
 * Case file, lawyer and designated recipient are named. {@code urlTelechargement} (presigned,
 * 10 minutes) and {@code commentaires} are only set on the detail {@code GET /publications/&#123;id&#125;},
 * and are {@code null} in lists, like a GED document URL.
 * <p>
 * Exception: {@code correspondance} is included in the {@code GET /portail/mes-publications} list,
 * the only screen where the lawyer can read what the DJ/DJA sent. {@code commentaires} is always
 * {@code null} for the lawyer: the deliberation between legal officer and DJ/DJA is never addressed
 * to them (US 5.5, RG-INT-07).
 */
public record PublicationResponse(
        Long id,
        TypePublication type,
        Long dossierId,
        Long avocatId,
        LocalDateTime dateDepot,
        StatutPublication statutValidation,
        String motifRejet,
        Long restrictionAccesId,
        Long audienceId,
        String contenu,
        TypeAutrePublication typeDocument,
        String referenceDossier,
        String avocatNom,
        LocalDate audienceDate,
        String restrictionAccesNom,
        String nomFichier,
        String urlTelechargement,
        List<CommentaireResponse> commentaires,
        CorrespondanceResponse correspondance) {
}
