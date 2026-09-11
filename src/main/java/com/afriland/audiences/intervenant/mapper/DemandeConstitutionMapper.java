package com.afriland.audiences.intervenant.mapper;

import com.afriland.audiences.intervenant.domain.DemandeConstitution;
import com.afriland.audiences.intervenant.web.dto.DemandeConstitutionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DemandeConstitutionMapper {

    @Mapping(source = "dossier.id", target = "dossierId")
    @Mapping(source = "prestataire.id", target = "prestataireId")
    @Mapping(source = "demandeur.id", target = "demandeurId")
    @Mapping(source = "validePar.id", target = "valideParId")
    @Mapping(source = "lettreDocument.id", target = "lettreDocumentId")
    DemandeConstitutionResponse toResponse(DemandeConstitution demande);
}
