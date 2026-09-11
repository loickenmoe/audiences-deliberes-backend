package com.afriland.audiences.decision.mapper;

import com.afriland.audiences.decision.domain.CondamnationPecuniaire;
import com.afriland.audiences.decision.web.dto.CondamnationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CondamnationMapper {

    @Mapping(source = "dossier.id", target = "dossierId")
    @Mapping(source = "etape.id", target = "etapeId")
    CondamnationResponse toResponse(CondamnationPecuniaire condamnation);
}
