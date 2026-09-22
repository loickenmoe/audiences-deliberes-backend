package com.afriland.audiences.publication.mapper;

import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.publication.domain.AutrePublication;
import com.afriland.audiences.publication.domain.CompteRenduAudience;
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
 * Mapping manuel (pas MapStruct) : {@link Publication} est une hiérarchie {@code JOINED} dont les
 * champs propres à chaque sous-type ({@link CompteRenduAudience}/{@link AutrePublication}) doivent
 * être aplatis conditionnellement dans {@link PublicationResponse} — plus direct qu'un mapper
 * déclaratif ici.
 */
@Component
public class PublicationMapper {

    /** Forme de liste (#59) : ni URL de fichier, ni commentaires, ni correspondance (Q-86). */
    public PublicationResponse toResponse(Publication publication) {
        return toResponse(publication, null, null, null);
    }

    /** Forme de détail (#32, Q-86) : URL pré-signée du fichier, commentaires et correspondance. */
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
            // Q-98 : la pièce jointe se lit comme celle d'une autre publication — même nom, même URL.
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

    public static String nomComplet(Utilisateur utilisateur) {
        if (utilisateur == null) {
            return null;
        }
        return Stream.of(utilisateur.getPrenom(), utilisateur.getNom())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
    }

    /** Clé {@code dossiers/{id}/{uuid}-{nom}} de {@code MinioStorageService} → nom d'origine du fichier. */
    static String nomFichier(String cle) {
        if (cle == null) {
            return null;
        }
        String dernier = cle.substring(cle.lastIndexOf('/') + 1);
        return dernier.length() > 37 && dernier.charAt(36) == '-' ? dernier.substring(37) : dernier;
    }
}
