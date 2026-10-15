package com.afriland.audiences.intervenant.mapper;

import com.afriland.audiences.intervenant.domain.DemandeConstitution;
import com.afriland.audiences.intervenant.domain.TypeIntervenant;
import com.afriland.audiences.intervenant.web.dto.DemandeConstitutionResponse;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Intervenant;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import org.hibernate.Hibernate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mapper(componentModel = "spring")
public interface DemandeConstitutionMapper {

    @Mapping(source = "dossier.id", target = "dossierId")
    @Mapping(source = "prestataire.id", target = "prestataireId")
    @Mapping(source = "demandeur.id", target = "demandeurId")
    @Mapping(source = "validePar.id", target = "valideParId")
    @Mapping(source = "lettreDocument.id", target = "lettreDocumentId")
    @Mapping(source = "dossier.reference", target = "referenceDossier")
    @Mapping(source = "prestataire.nom", target = "prestataireNom")
    @Mapping(target = "prestataireType", expression = "java(typeDe(demande.getPrestataire()))")
    @Mapping(target = "demandeurNom", expression = "java(nomComplet(demande.getDemandeur()))")
    @Mapping(target = "valideParNom", expression = "java(nomComplet(demande.getValidePar()))")
    // Le droit d'écrire dépend de **qui lit**, que le mappeur ignore : le service le pose.
    @Mapping(target = "redactionPossible", ignore = true)
    DemandeConstitutionResponse toResponse(DemandeConstitution demande);

    /**
     * Q-87 : le prestataire est chargé à la demande — un proxy d'{@link Intervenant} n'est jamais une
     * instance d'{@link Avocat}. On compare donc l'entité réelle.
     */
    default TypeIntervenant typeDe(Intervenant prestataire) {
        if (prestataire == null) {
            return null;
        }
        return Hibernate.unproxy(prestataire) instanceof Avocat ? TypeIntervenant.AVOCAT : TypeIntervenant.AUTRE_PRESTATAIRE;
    }

    default String nomComplet(Utilisateur utilisateur) {
        if (utilisateur == null) {
            return null;
        }
        return Stream.of(utilisateur.getPrenom(), utilisateur.getNom())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
    }
}
