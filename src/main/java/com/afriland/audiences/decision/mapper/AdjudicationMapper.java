package com.afriland.audiences.decision.mapper;

import com.afriland.audiences.decision.domain.Adjudication;
import com.afriland.audiences.decision.web.dto.AdjudicationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AdjudicationMapper {

    @Mapping(source = "dossier.id", target = "dossierId")
    @Mapping(source = "etape.id", target = "etapeId")
    AdjudicationResponse toResponse(Adjudication adjudication);
}
