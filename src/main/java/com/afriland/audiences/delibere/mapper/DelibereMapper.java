package com.afriland.audiences.delibere.mapper;

import com.afriland.audiences.delibere.domain.Delibere;
import com.afriland.audiences.delibere.web.dto.DelibereResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DelibereMapper {

    @Mapping(source = "dossier.id", target = "dossierId")
    @Mapping(source = "etape.id", target = "etapeId")
    DelibereResponse toResponse(Delibere delibere);
}
