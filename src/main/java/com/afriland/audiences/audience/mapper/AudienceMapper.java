package com.afriland.audiences.audience.mapper;

import com.afriland.audiences.audience.domain.Audience;
import com.afriland.audiences.audience.web.dto.AudienceResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AudienceMapper {

    @Mapping(source = "dossier.id", target = "dossierId")
    @Mapping(source = "etape.id", target = "etapeId")
    AudienceResponse toResponse(Audience audience);
}
