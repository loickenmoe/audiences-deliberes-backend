package com.afriland.audiences.alerte.mapper;

import com.afriland.audiences.alerte.domain.Alerte;
import com.afriland.audiences.alerte.web.dto.AlerteResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AlerteMapper {

    @Mapping(source = "destinataire.id", target = "destinataireId")
    AlerteResponse toResponse(Alerte alerte);
}
