package com.afriland.audiences.publication.mapper;

import com.afriland.audiences.publication.domain.AutrePublication;
import com.afriland.audiences.publication.domain.CompteRenduAudience;
import com.afriland.audiences.publication.domain.Publication;
import com.afriland.audiences.publication.domain.TypePublication;
import com.afriland.audiences.publication.web.dto.PublicationResponse;
import org.springframework.stereotype.Component;

/**
 * Mapping manuel (pas MapStruct) : {@link Publication} est une hiérarchie {@code JOINED} dont les
 * champs propres à chaque sous-type ({@link CompteRenduAudience}/{@link AutrePublication}) doivent
 * être aplatis conditionnellement dans {@link PublicationResponse} — plus direct qu'un mapper
 * déclaratif ici.
 */
@Component
public class PublicationMapper {

    public PublicationResponse toResponse(Publication publication) {
        Long audienceId = null;
        String contenu = null;
        com.afriland.audiences.publication.domain.TypeAutrePublication typeDocument = null;

        if (publication instanceof CompteRenduAudience cr) {
            audienceId = cr.getAudience() != null ? cr.getAudience().getId() : null;
            contenu = cr.getContenu();
        } else if (publication instanceof AutrePublication autre) {
            typeDocument = autre.getTypeDocument();
        }

        return new PublicationResponse(
                publication.getId(),
                publication instanceof CompteRenduAudience ? TypePublication.CR_AUDIENCE : TypePublication.AUTRE,
                publication.getDossier() != null ? publication.getDossier().getId() : null,
                publication.getAvocat() != null ? publication.getAvocat().getId() : null,
                publication.getDateDepot(),
                publication.getStatutValidation(),
                publication.getMotifRejet(),
                publication.getRestrictionAcces() != null ? publication.getRestrictionAcces().getId() : null,
                audienceId,
                contenu,
                typeDocument);
    }
}
