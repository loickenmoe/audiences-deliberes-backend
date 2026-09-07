package com.afriland.audiences.frais.mapper;

import com.afriland.audiences.frais.domain.DemandeFrais;
import com.afriland.audiences.frais.web.dto.DemandeFraisResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DemandeFraisMapper {

    @Mapping(source = "dossier.id", target = "dossierId")
    @Mapping(source = "avocat.id", target = "avocatId")
    DemandeFraisResponse toResponse(DemandeFrais demande);
}
