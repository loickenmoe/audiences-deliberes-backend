package com.afriland.audiences.ged.mapper;

import com.afriland.audiences.ged.domain.DemandeSuppression;
import com.afriland.audiences.ged.web.dto.DemandeSuppressionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DemandeSuppressionMapper {

    @Mapping(source = "document.id", target = "documentId")
    @Mapping(source = "demandeur.id", target = "demandeurId")
    @Mapping(source = "validePar.id", target = "valideParId")
    DemandeSuppressionResponse toResponse(DemandeSuppression demande);
}
