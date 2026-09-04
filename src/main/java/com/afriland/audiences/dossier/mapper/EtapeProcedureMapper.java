package com.afriland.audiences.dossier.mapper;

import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.web.dto.EtapeProcedureResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EtapeProcedureMapper {

    @Mapping(source = "dossier.id", target = "dossierId")
    EtapeProcedureResponse toResponse(EtapeProcedure etape);
}
