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
 * (pré-signée, 10 minutes) et {@code commentaires} ne sont renseignés que sur le détail
 * {@code GET /publications/&#123;id&#125;} — {@code null} sur la liste, comme l'URL d'un document GED (#43).
 * <p>
 * {@code correspondance} fait exception depuis QF-58 : la liste {@code GET /portail/mes-publications}
 * la porte, parce que c'est le seul écran où l'avocat peut lire ce que le DJ/DJA lui a transmis — il
 * en recevait la notification sans qu'aucune page ne l'affiche. {@code commentaires}, lui, reste
 * {@code null} pour l'avocat <b>en toute circonstance</b> : la délibération entre le juriste et le
 * DJ/DJA ne lui est jamais adressée (US 5.5, RG-INT-07).
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
