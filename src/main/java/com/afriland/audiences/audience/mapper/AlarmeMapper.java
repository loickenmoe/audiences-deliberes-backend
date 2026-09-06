package com.afriland.audiences.audience.mapper;

import com.afriland.audiences.audience.domain.Alarme;
import com.afriland.audiences.audience.web.dto.AlarmeResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AlarmeMapper {

    @Mapping(source = "dossier.id", target = "dossierId")
    @Mapping(source = "etape.id", target = "etapeId")
    @Mapping(source = "alarmePrecedente.id", target = "alarmePrecedenteId")
    @Mapping(source = "createdBy.id", target = "createdBy")
    AlarmeResponse toResponse(Alarme alarme);
}
