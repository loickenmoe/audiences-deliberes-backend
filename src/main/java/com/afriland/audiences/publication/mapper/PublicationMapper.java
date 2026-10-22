package com.afriland.audiences.publication.mapper;

import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.publication.domain.AutrePublication;
import com.afriland.audiences.publication.domain.CompteRenduAudience;
import com.afriland.audiences.publication.domain.CorrespondanceAvocat;
import com.afriland.audiences.publication.domain.Publication;
import com.afriland.audiences.publication.domain.TypeAutrePublication;
import com.afriland.audiences.publication.domain.TypePublication;
import com.afriland.audiences.publication.web.dto.CommentaireResponse;
import com.afriland.audiences.publication.web.dto.CorrespondanceResponse;
import com.afriland.audiences.publication.web.dto.PublicationResponse;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Hand-written mapping (not MapStruct): {@link Publication} is a {@code JOINED} hierarchy whose
 * subtype-specific fields ({@link CompteRenduAudience}/{@link AutrePublication}) must be flattened
 * conditionally into {@link PublicationResponse}.
 */
@Component
public class PublicationMapper {

    /** List form: no file URL, comments or correspondence. */
    public PublicationResponse toResponse(Publication publication) {
        return toResponse(publication, null, null, null);
    }

    /** Detail form: presigned file URL, comments and correspondence. */
    public PublicationResponse toResponse(Publication publication, String urlTelechargement,
                                          List<CommentaireResponse> commentaires, CorrespondanceResponse correspondance) {
        Long audienceId = null;
        LocalDate audienceDate = null;
        String contenu = null;
        TypeAutrePublication typeDocument = null;
        String nomFichier = null;

        if (publication instanceof CompteRenduAudience cr) {
            if (cr.getAudience() != null) {
                audienceId = cr.getAudience().getId();
                audienceDate = cr.getAudience().getDatePlanifiee();
            }
            contenu = cr.getContenu();
            // The attachment is read like that of an other publication: same name, same URL.
            nomFichier = nomFichier(cr.getCheminStockage());
        } else if (publication instanceof AutrePublication autre) {
            typeDocument = autre.getTypeDocument();
            nomFichier = nomFichier(autre.getCheminStockage());
        }

        Dossier dossier = publication.getDossier();
        Avocat avocat = publication.getAvocat();
        Utilisateur restriction = publication.getRestrictionAcces();
        return new PublicationResponse(
                publication.getId(),
                publication instanceof CompteRenduAudience ? TypePublication.CR_AUDIENCE : TypePublication.AUTRE,
                dossier != null ? dossier.getId() : null,
                avocat != null ? avocat.getId() : null,
                publication.getDateDepot(),
                publication.getStatutValidation(),
                publication.getMotifRejet(),
                restriction != null ? restriction.getId() : null,
                audienceId,
                contenu,
                typeDocument,
                dossier != null ? dossier.getReference() : null,
                avocat != null ? avocat.getNom() : null,
                audienceDate,
                nomComplet(restriction),
                nomFichier,
                urlTelechargement,
                commentaires,
                correspondance);
    }

    /**
     * A correspondence, as read on both sides.
     *
     * <p>Lives here rather than in a service because <b>two</b> callers read it: the legal officer on a
     * publication's detail, and the lawyer on their portal. Written twice, it would diverge at the
     * first added field.
     */
    public static CorrespondanceResponse versReponse(Long publicationId, CorrespondanceAvocat correspondance) {
        Utilisateur auteur = correspondance.getAuteur();
        return new CorrespondanceResponse(correspondance.getId(), publicationId,
                auteur != null ? auteur.getId() : null, nomComplet(auteur),
                correspondance.getContenu(), correspondance.getCreatedAt());
    }

    public static String nomComplet(Utilisateur utilisateur) {
        if (utilisateur == null) {
            return null;
        }
        return Stream.of(utilisateur.getPrenom(), utilisateur.getNom())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
    }

    /** {@code MinioStorageService} key {@code dossiers/{id}/{uuid}-{nom}} to the original file name. */
    static String nomFichier(String cle) {
        if (cle == null) {
            return null;
        }
        String dernier = cle.substring(cle.lastIndexOf('/') + 1);
        return dernier.length() > 37 && dernier.charAt(36) == '-' ? dernier.substring(37) : dernier;
    }
}
