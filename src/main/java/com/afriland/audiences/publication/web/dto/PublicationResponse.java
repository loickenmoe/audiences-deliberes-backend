package com.afriland.audiences.publication.web.dto;

import com.afriland.audiences.publication.domain.StatutPublication;
import com.afriland.audiences.publication.domain.TypeAutrePublication;
import com.afriland.audiences.publication.domain.TypePublication;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Représentation aplatie de {@link com.afriland.audiences.publication.domain.Publication} — les
 * champs propres à un sous-type ({@code audienceId}/{@code audienceDate}/{@code contenu} pour un
 * compte rendu, {@code typeDocument}/{@code nomFichier} pour une autre publication) sont {@code null}
 * pour l'autre sous-type.
 * <p>
 * Depuis Q-86 : dossier, avocat et destinataire désigné sont nommés. {@code urlTelechargement}
 * (pré-signée, 10 minutes), {@code commentaires} et {@code correspondance} ne sont renseignés que
 * sur le détail {@code GET /publications/{id}} — {@code null} sur la liste, comme l'URL d'un document
 * GED (#43).
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
